package com.example.AOD.recommend.log;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogQueueTest {

    private static LogRecord rec() {
        return new LogRecord() {
            @Override public String sql() { return "INSERT test"; }
            @Override public void bind(PreparedStatement ps) { }
        };
    }

    @Test
    void dropsAndCountsWhenFull() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LogQueue queue = new LogQueue(registry, 2);

        assertTrue(queue.offer(rec()));
        assertTrue(queue.offer(rec()));
        assertFalse(queue.offer(rec()), "가득 차면 버린다 — 요청 스레드를 막지 않는다");

        assertEquals(1.0, registry.get("rec.log.dropped").counter().count());
        assertEquals(2, queue.size());
    }

    @Test
    void drainToHonorsMax() {
        LogQueue queue = new LogQueue(new SimpleMeterRegistry(), 10);
        for (int i = 0; i < 5; i++) queue.offer(rec());

        List<LogRecord> out = new ArrayList<>();
        assertEquals(3, queue.drainTo(out, 3));
        assertEquals(2, queue.size());
    }
}
