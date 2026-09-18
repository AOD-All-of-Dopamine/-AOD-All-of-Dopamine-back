package com.example.AOD.recommend.log;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogWriterTest {

    private static final OffsetDateTime TS = OffsetDateTime.of(2026, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC);

    private record Deduped(UUID eventId, OffsetDateTime serverTs) implements DedupedLogRecord {
        @Override public String sql() { return "INSERT event"; }
        @Override public void bind(PreparedStatement ps) { }
    }

    private record Plain(String value) implements LogRecord {
        @Override public String sql() { return "INSERT plain"; }
        @Override public void bind(PreparedStatement ps) throws SQLException { ps.setString(1, value); }
    }

    private static LogWriter writer(LogQueue queue, JdbcTemplate jdbc, SimpleMeterRegistry registry, boolean threadEnabled) {
        return new LogWriter(queue, jdbc, TransactionOperations.withoutTransaction(), registry, threadEnabled);
    }

    @Test
    void writesOnlyEventsThatPassEventSeenAndGroupsBySql() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogWriter writer = writer(new LogQueue(registry), jdbc, registry, false);
        UUID first = UUID.randomUUID();

        // event_seen: 첫 행은 새 것(1), 둘째 행은 중복(0)
        when(jdbc.batchUpdate(eq(LogWriter.EVENT_SEEN_SQL), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1, 0});

        writer.write(List.of(new Deduped(first, TS), new Deduped(UUID.randomUUID(), TS), new Plain("p")));

        ArgumentCaptor<BatchPreparedStatementSetter> seenSetter = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(eq(LogWriter.EVENT_SEEN_SQL), seenSetter.capture());
        PreparedStatement seenPs = mock(PreparedStatement.class);
        seenSetter.getValue().setValues(seenPs, 0);
        verify(seenPs).setObject(1, first);
        verify(seenPs).setObject(2, TS);

        ArgumentCaptor<BatchPreparedStatementSetter> eventSetter = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(eq("INSERT event"), eventSetter.capture());
        assertEquals(1, eventSetter.getValue().getBatchSize(), "중복 행은 본 테이블에 가지 않는다");

        ArgumentCaptor<BatchPreparedStatementSetter> plainSetter = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(eq("INSERT plain"), plainSetter.capture());
        assertEquals(1, plainSetter.getValue().getBatchSize());
        PreparedStatement plainPs = mock(PreparedStatement.class);
        plainSetter.getValue().setValues(plainPs, 0);
        verify(plainPs).setString(1, "p");   // 레코드의 bind() 가 실제로 불린다

        assertEquals(2.0, registry.get("rec.log.written").counter().count());
        assertEquals(1.0, registry.get("rec.log.duplicates").counter().count());
    }

    @Test
    void successNoInfoCountsPassDedupe() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogWriter writer = writer(new LogQueue(registry), jdbc, registry, false);
        when(jdbc.batchUpdate(eq(LogWriter.EVENT_SEEN_SQL), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{java.sql.Statement.SUCCESS_NO_INFO});

        writer.write(List.of(new Deduped(UUID.randomUUID(), TS)));

        assertEquals(1.0, registry.get("rec.log.written").counter().count(), "판별 불가(-2)는 통과시킨다");
        assertEquals(0.0, registry.get("rec.log.duplicates").counter().count());
    }

    @Test
    void flushNowDrainsQueueAndSurvivesWriteFailureWithoutCountingWritten() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogQueue queue = new LogQueue(registry);
        LogWriter writer = writer(queue, jdbc, registry, false);
        when(jdbc.batchUpdate(eq("INSERT plain"), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new RuntimeException("db down"));

        queue.offer(new Plain("a"));
        queue.offer(new Plain("b"));

        assertEquals(2, writer.flushNow(), "실패해도 예외를 밖으로 던지지 않고 큐는 비운다");
        assertEquals(0, queue.size());
        assertEquals(2.0, registry.get("rec.log.failed").counter().count());
        assertEquals(0.0, registry.get("rec.log.written").counter().count(), "실패한 배치는 written 에 세지 않는다");
    }

    @Test
    void backgroundLoopSplitsIntoBatchesOfAtMost200AndStopDrains() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogQueue queue = new LogQueue(registry);
        LogWriter writer = writer(queue, jdbc, registry, true);
        for (int i = 0; i < 300; i++) queue.offer(new Plain("r" + i));

        writer.start();
        assertTrue(writer.isRunning());
        long deadline = System.currentTimeMillis() + 5_000;
        while (registry.get("rec.log.written").counter().count() < 300 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        queue.offer(new Plain("late"));   // stop() 의 종료 flush 가 가져가야 한다
        writer.stop();

        assertEquals(301.0, registry.get("rec.log.written").counter().count());
        assertEquals(0, queue.size());
        ArgumentCaptor<BatchPreparedStatementSetter> setters = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc, atLeast(2)).batchUpdate(eq("INSERT plain"), setters.capture());
        for (BatchPreparedStatementSetter s : setters.getAllValues()) {
            assertTrue(s.getBatchSize() <= LogWriter.BATCH_MAX, "한 배치는 200건을 넘지 않는다");
        }
    }
}
