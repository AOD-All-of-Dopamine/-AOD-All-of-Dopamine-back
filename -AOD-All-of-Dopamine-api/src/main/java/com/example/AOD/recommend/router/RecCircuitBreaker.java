package com.example.AOD.recommend.router;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 의존성 없는 서킷 (REC_TAB_DESIGN §6-2 — Resilience4j 는 이 레포에 없고 도입하지 않는다).
 * 연속 실패 10회 → 30초 열림 → 30초 뒤 1건만 시험(반열림) → 성공이면 닫고, 실패면 다시 30초.
 * 메서드가 전부 synchronized 다 — 호출 빈도가 요청당 1~2회라 경합이 문제가 되지 않는다.
 */
@Component
public class RecCircuitBreaker {

    public static final int FAILURE_THRESHOLD = 10;
    public static final long OPEN_MS = 30_000L;

    private final Clock clock;

    private int consecutiveFailures;
    private boolean open;
    private long openedAtMs;
    private boolean probeInFlight;

    @Autowired
    public RecCircuitBreaker() {
        this(Clock.systemUTC());
    }

    RecCircuitBreaker(Clock clock) {
        this.clock = clock;
    }

    /** 호출해도 되는가. 열린 동안 false, 30초가 지나면 딱 한 건만 통과시킨다. */
    public synchronized boolean allow() {
        if (!open) return true;
        if (probeInFlight) return false;
        if (clock.millis() - openedAtMs < OPEN_MS) return false;
        probeInFlight = true;
        return true;
    }

    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        open = false;
        probeInFlight = false;
    }

    /**
     * 라우터가 답은 했지만 건강 여부를 알 수 없는 경우 — 4xx(우리 요청 모양이 틀렸다).
     * 성공도 실패도 아니라서 연속 실패 수를 건드리지 않고, 반열림 시험권만 돌려준다.
     * 시험 중이었다면 창을 다시 연다 — 시험은 끝났으니 다음 시험은 30초 뒤여야 한다.
     */
    public synchronized void recordIndeterminate() {
        if (probeInFlight) {
            probeInFlight = false;
            openedAtMs = clock.millis();
        }
    }

    public synchronized void recordFailure() {
        if (probeInFlight) {                 // 반열림 시험 실패 → 다시 30초 연다
            probeInFlight = false;
            open = true;
            openedAtMs = clock.millis();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= FAILURE_THRESHOLD) {
            open = true;
            openedAtMs = clock.millis();
        }
    }

    public synchronized boolean isOpen() {
        return open;
    }
}
