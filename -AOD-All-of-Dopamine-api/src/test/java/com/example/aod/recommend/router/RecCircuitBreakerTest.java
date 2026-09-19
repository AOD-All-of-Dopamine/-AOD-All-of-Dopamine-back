package com.example.AOD.recommend.router;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecCircuitBreakerTest {

    /** 테스트에서 시간을 밀기 위한 시계. Clock.fixed 는 움직일 수 없다. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-19T12:00:00Z");

        void advanceMillis(long millis) { now = now.plusMillis(millis); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final MutableClock clock = new MutableClock();
    private final RecCircuitBreaker breaker = new RecCircuitBreaker(clock);

    private void fail(int times) {
        for (int i = 0; i < times; i++) {
            assertTrue(breaker.allow(), "닫힌 동안에는 계속 통과시킨다");
            breaker.recordFailure();
        }
    }

    @Test
    void staysClosedBelowThreshold() {
        fail(RecCircuitBreaker.FAILURE_THRESHOLD - 1);
        assertTrue(breaker.allow());
        assertFalse(breaker.isOpen());
    }

    @Test
    void successResetsTheFailureRun() {
        fail(RecCircuitBreaker.FAILURE_THRESHOLD - 1);
        breaker.recordSuccess();
        fail(RecCircuitBreaker.FAILURE_THRESHOLD - 1);
        assertTrue(breaker.allow(), "연속이 끊기면 다시 센다");
    }

    @Test
    void opensAfterTenConsecutiveFailuresAndBlocksForThirtySeconds() {
        fail(RecCircuitBreaker.FAILURE_THRESHOLD);

        assertTrue(breaker.isOpen());
        assertFalse(breaker.allow());
        clock.advanceMillis(RecCircuitBreaker.OPEN_MS - 1);
        assertFalse(breaker.allow());
    }

    @Test
    void halfOpenLetsExactlyOneProbeThrough() {
        fail(RecCircuitBreaker.FAILURE_THRESHOLD);
        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);

        assertTrue(breaker.allow(), "30초 뒤 1건만 시험");
        assertFalse(breaker.allow(), "시험이 끝나기 전에는 더 보내지 않는다");
    }

    @Test
    void failedProbeReopensForAnotherThirtySeconds() {
        fail(RecCircuitBreaker.FAILURE_THRESHOLD);
        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);
        assertTrue(breaker.allow());

        breaker.recordFailure();

        assertTrue(breaker.isOpen());
        assertFalse(breaker.allow());
        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);
        assertTrue(breaker.allow(), "다시 30초 뒤에 또 1건");
    }

    @Test
    void successfulProbeClosesTheCircuit() {
        fail(RecCircuitBreaker.FAILURE_THRESHOLD);
        clock.advanceMillis(RecCircuitBreaker.OPEN_MS);
        assertTrue(breaker.allow());

        breaker.recordSuccess();

        assertFalse(breaker.isOpen());
        assertTrue(breaker.allow());
        assertTrue(breaker.allow());
    }
}
