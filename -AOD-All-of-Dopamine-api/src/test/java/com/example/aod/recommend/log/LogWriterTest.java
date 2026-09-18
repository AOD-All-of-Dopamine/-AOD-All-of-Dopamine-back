package com.example.AOD.recommend.log;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LogWriterTest {

    private static final OffsetDateTime TS = OffsetDateTime.of(2026, 9, 18, 0, 0, 0, 0, ZoneOffset.UTC);

    private record Deduped(UUID eventId, OffsetDateTime serverTs) implements DedupedLogRecord {
        @Override public String sql() { return "INSERT event"; }
        @Override public void bind(PreparedStatement ps) { }
    }

    private record Plain() implements LogRecord {
        @Override public String sql() { return "INSERT plain"; }
        @Override public void bind(PreparedStatement ps) { }
    }

    @Test
    void writesOnlyEventsThatPassEventSeenAndGroupsBySql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogWriter writer = new LogWriter(new LogQueue(registry), jdbc, registry, false);

        // event_seen: 첫 행은 새 것(1), 둘째 행은 중복(0)
        when(jdbc.batchUpdate(eq(LogWriter.EVENT_SEEN_SQL), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1, 0});
        when(jdbc.batchUpdate(eq("INSERT event"), any(BatchPreparedStatementSetter.class))).thenReturn(new int[]{1});
        when(jdbc.batchUpdate(eq("INSERT plain"), any(BatchPreparedStatementSetter.class))).thenReturn(new int[]{1});

        writer.write(List.of(new Deduped(UUID.randomUUID(), TS), new Deduped(UUID.randomUUID(), TS), new Plain()));

        ArgumentCaptor<BatchPreparedStatementSetter> eventSetter = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(eq("INSERT event"), eventSetter.capture());
        assertEquals(1, eventSetter.getValue().getBatchSize(), "중복 행은 본 테이블에 가지 않는다");

        ArgumentCaptor<BatchPreparedStatementSetter> plainSetter = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        verify(jdbc).batchUpdate(eq("INSERT plain"), plainSetter.capture());
        assertEquals(1, plainSetter.getValue().getBatchSize());

        assertEquals(2.0, registry.get("rec.log.written").counter().count());
        assertEquals(1.0, registry.get("rec.log.duplicates").counter().count());
    }

    @Test
    void flushNowDrainsQueueAndSurvivesWriteFailure() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogQueue queue = new LogQueue(registry);
        LogWriter writer = new LogWriter(queue, jdbc, registry, false);
        when(jdbc.batchUpdate(eq("INSERT plain"), any(BatchPreparedStatementSetter.class)))
                .thenThrow(new RuntimeException("db down"));

        queue.offer(new Plain());
        queue.offer(new Plain());

        assertEquals(2, writer.flushNow(), "실패해도 예외를 밖으로 던지지 않고 큐는 비운다");
        assertEquals(0, queue.size());
        assertEquals(2.0, registry.get("rec.log.failed").counter().count());
    }
}
