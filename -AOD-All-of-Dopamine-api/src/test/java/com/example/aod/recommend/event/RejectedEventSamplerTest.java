package com.example.AOD.recommend.event;

import com.example.AOD.recommend.log.LogQueue;
import com.example.AOD.recommend.log.LogRecord;
import com.example.AOD.recommend.log.RejectedEventLogRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RejectedEventSamplerTest {

    @Test
    void storesOnlyWhenDieLandsInOnePercent() {
        LogQueue queue = new LogQueue(new SimpleMeterRegistry());
        int[] die = {0};
        RejectedEventSampler sampler = new RejectedEventSampler(queue, new ObjectMapper(), () -> die[0], Clock.systemUTC());

        assertTrue(sampler.maybeStore(Map.of("type", "bogus"), "type_not_allowed"));
        die[0] = 57;
        assertFalse(sampler.maybeStore(Map.of("type", "bogus"), "type_not_allowed"));

        List<LogRecord> out = new ArrayList<>();
        queue.drainTo(out, 10);
        assertEquals(1, out.size());
        RejectedEventLogRecord r = (RejectedEventLogRecord) out.get(0);
        assertEquals("type_not_allowed", r.reason());
        assertTrue(r.rawJson().contains("\"type\":\"bogus\""));
    }
}
