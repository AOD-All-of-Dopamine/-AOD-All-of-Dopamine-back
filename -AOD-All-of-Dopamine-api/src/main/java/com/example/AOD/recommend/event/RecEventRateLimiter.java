package com.example.AOD.recommend.event;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 키(anonId)당 분당 묶음 수 제한 — 고정 창. 메모리 전용이라 인스턴스가 하나인 지금 구성에 맞다. */
@Component
public class RecEventRateLimiter {

    static final int DEFAULT_LIMIT_PER_MINUTE = 120;
    static final int CLEANUP_THRESHOLD = 50_000;

    private record Window(long minute, AtomicInteger count) { }

    private final int limitPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** Spring 은 이 기본 생성자를 쓴다. (int, Clock) 생성자는 테스트용. */
    public RecEventRateLimiter() {
        this(DEFAULT_LIMIT_PER_MINUTE, Clock.systemUTC());
    }

    RecEventRateLimiter(int limitPerMinute, Clock clock) {
        this.limitPerMinute = limitPerMinute;
        this.clock = clock;
    }

    public boolean allow(String key) {
        long minute = clock.instant().getEpochSecond() / 60;
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.minute() < minute);
        }
        Window w = windows.compute(key, (k, old) ->
                old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        return w.count().incrementAndGet() <= limitPerMinute;
    }
}
