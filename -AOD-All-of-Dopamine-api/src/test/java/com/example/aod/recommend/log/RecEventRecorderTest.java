package com.example.AOD.recommend.log;

import com.example.AOD.recommend.context.RecContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RecEventRecorderTest {

    private final LogQueue queue = new LogQueue(new SimpleMeterRegistry());
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-18T01:02:03Z"), ZoneOffset.UTC);
    private final RecEventRecorder recorder = new RecEventRecorder(queue, new ObjectMapper(), clock);

    private List<LogRecord> drain() {
        List<LogRecord> out = new ArrayList<>();
        queue.drainTo(out, 100);
        return out;
    }

    @Test
    void reactionChangedCarriesFromToAndContext() {
        UUID iid = UUID.randomUUID();
        RecContext ctx = new RecContext("rec_tab", null, iid, null, null);

        recorder.reactionChanged(7L, 42L, "LIKE", "DISLIKE", ctx);

        EventLogRecord e = (EventLogRecord) drain().get(0);
        assertEquals("reaction_changed", e.eventType());
        assertEquals("server", e.origin());
        assertEquals(7L, e.userId());
        assertEquals(42L, e.contentId());
        assertEquals(iid, e.impressionId());
        assertEquals("rec_tab", e.surface());
        assertTrue(e.payloadJson().contains("\"from\":\"LIKE\""));
        assertTrue(e.payloadJson().contains("\"to\":\"DISLIKE\""));
        assertEquals("2026-09-18T01:02:03Z", e.serverTs().toString());
        assertNull(e.clientTs());
    }

    @Test
    void bookmarkAndReviewEvents() {
        recorder.bookmarkChanged(7L, 42L, true, RecContext.EMPTY);
        recorder.reviewSaved(7L, 42L, 4.5, true, RecContext.EMPTY);

        List<LogRecord> out = drain();
        EventLogRecord bookmark = (EventLogRecord) out.get(0);
        EventLogRecord review = (EventLogRecord) out.get(1);
        assertEquals("bookmark_changed", bookmark.eventType());
        assertTrue(bookmark.payloadJson().contains("\"state\":\"on\""));
        assertEquals("review_saved", review.eventType());
        assertTrue(review.payloadJson().contains("\"rating\":4.5"));
        assertTrue(review.payloadJson().contains("\"isNew\":true"));
    }

    @Test
    void clientAgentIsRecordedOncePerSession() {
        UUID session = UUID.randomUUID();
        recorder.clientAgentOnce(session, "Mozilla/5.0");
        recorder.clientAgentOnce(session, "Mozilla/5.0");

        assertEquals(1, drain().size());
    }

    @Test
    void eventRecordBindsNullsWithSqlTypes() throws Exception {
        recorder.reactionChanged(null, 42L, "NONE", "LIKE", RecContext.EMPTY);
        EventLogRecord e = (EventLogRecord) drain().get(0);
        PreparedStatement ps = mock(PreparedStatement.class);

        e.bind(ps);

        verify(ps).setObject(1, e.eventId());
        verify(ps).setString(3, "reaction_changed");
        verify(ps).setNull(5, Types.BIGINT);     // user_id
        verify(ps).setNull(6, Types.OTHER);      // anon_id
        verify(ps).setLong(8, 42L);              // content_id
        verify(ps).setString(12, e.payloadJson());
    }

    @Test
    void serverEventWaitsForCommitWhenTransactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            recorder.reactionChanged(7L, 42L, "NONE", "LIKE", RecContext.EMPTY);
            assertEquals(0, drain().size(), "커밋 전에는 큐에 넣지 않는다");

            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) s.afterCommit();
            assertEquals(1, drain().size());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rolledBackTransactionLeavesNoEvent() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            recorder.bookmarkChanged(7L, 42L, true, RecContext.EMPTY);
            for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
                s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertEquals(0, drain().size(), "롤백되면 이벤트가 남지 않는다");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void clientAgentIsRetriedAfterQueueDrop() {
        LogQueue tiny = new LogQueue(new SimpleMeterRegistry(), 1);
        RecEventRecorder r = new RecEventRecorder(tiny, new ObjectMapper(), clock);
        UUID session = UUID.randomUUID();
        r.bookmarkChanged(7L, 42L, true, RecContext.EMPTY);   // 큐를 채운다 (트랜잭션 밖이라 바로 들어간다)

        r.clientAgentOnce(session, "Mozilla/5.0");            // 가득 차서 버려진다
        List<LogRecord> first = new ArrayList<>();
        tiny.drainTo(first, 10);
        assertEquals(1, first.size());
        assertTrue(first.get(0) instanceof EventLogRecord);

        r.clientAgentOnce(session, "Mozilla/5.0");            // 버려졌던 세션은 다시 기록된다
        List<LogRecord> second = new ArrayList<>();
        tiny.drainTo(second, 10);
        assertEquals(1, second.size());
        assertTrue(second.get(0) instanceof ClientAgentLogRecord);
    }
}
