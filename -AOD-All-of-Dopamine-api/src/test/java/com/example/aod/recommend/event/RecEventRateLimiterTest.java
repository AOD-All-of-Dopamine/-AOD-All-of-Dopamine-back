package com.example.AOD.recommend.event;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecEventRateLimiterTest {

    /** 시간을 손으로 움직이는 Clock. */
    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void plusSeconds(long s) { now = now.plusSeconds(s); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void allowsUpToLimitPerMinuteThenBlocksThenResets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T00:00:00Z"));
        RecEventRateLimiter limiter = new RecEventRateLimiter(3, clock);

        assertTrue(limiter.allow("anon-a"));
        assertTrue(limiter.allow("anon-a"));
        assertTrue(limiter.allow("anon-a"));
        assertFalse(limiter.allow("anon-a"), "한도를 넘으면 거절");
        assertTrue(limiter.allow("anon-b"), "키마다 따로 센다");

        clock.plusSeconds(60);
        assertTrue(limiter.allow("anon-a"), "다음 분에는 다시 허용");
    }

    @Test
    void refusesNewKeysWhenMapIsSaturatedWithCurrentMinuteKeys() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-18T00:00:00Z"));
        RecEventRateLimiter limiter = new RecEventRateLimiter(3, clock);
        for (int i = 0; i <= RecEventRateLimiter.CLEANUP_THRESHOLD; i++) limiter.allow("k" + i);

        assertFalse(limiter.allow("brand-new-key"), "맵이 가득 차면 새 키는 거절한다");
        assertTrue(limiter.allow("k1"), "이미 있는 키는 한도 안에서 계속 허용한다");

        clock.plusSeconds(60);
        assertTrue(limiter.allow("brand-new-key"), "다음 분에는 지난 창이 비워져 다시 받는다");
    }
}
