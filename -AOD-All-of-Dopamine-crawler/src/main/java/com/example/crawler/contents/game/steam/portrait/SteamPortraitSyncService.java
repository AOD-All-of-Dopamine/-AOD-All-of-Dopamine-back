package com.example.crawler.contents.game.steam.portrait;

import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.Portrait;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.SteamPortraitException;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitStore.Checked;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitStore.Target;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Steam 세로 표지 동기화 (설계: 프론트 docs/superpowers/specs/2026-10-01-game-portrait-cover-design.md).
 *
 * <ul>
 *   <li>매일 05:10(KST) 최대 {@value #DAILY_CALLS}호출 — 묶음 {@value SteamPortraitClient#MAX_BATCH}개, 묶음 사이 2초</li>
 *   <li>스케줄러 스레드는 단일 스레드라 여기서 오래 돌면 수집 전체가 멈춘다 — 전용 실행기로 넘기고 바로 돌아온다</li>
 *   <li>단일 실행 잠금 — 예약 · 관리자 실행이 겹치면 뒤의 것은 건너뛴다(같은 대상을 두 번 묻지 않게)</li>
 *   <li>호출 실패(타임아웃 · 429 · 5xx …)면 그 실행을 멈춘다 — 확인 시각이 안 남아 다음 실행이 이어 간다</li>
 *   <li>실행 하나의 시간 상한 15분</li>
 * </ul>
 */
@Slf4j
@Service
public class SteamPortraitSyncService {

    public static final int DAILY_CALLS = 40;
    public static final int MAX_CALLS = 1_000;
    static final Duration TIME_LIMIT = Duration.ofMinutes(15);
    static final long PAUSE_MS = 2_000;

    interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    /** 실행 한 번의 결과 (로그 · 테스트). */
    public record Report(int calls, int asked, int covers, int none, int missing, boolean failed, boolean timedOut) { }

    private final SteamPortraitStore store;
    private final SteamPortraitClient client;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Sleeper sleeper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "steam-portrait-sync");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0);

    @Autowired
    public SteamPortraitSyncService(SteamPortraitStore store, SteamPortraitClient client, MeterRegistry meterRegistry) {
        this(store, client, meterRegistry, Clock.systemUTC(), Thread::sleep);
    }

    SteamPortraitSyncService(SteamPortraitStore store, SteamPortraitClient client, MeterRegistry meterRegistry,
                             Clock clock, Sleeper sleeper) {
        this.store = store;
        this.client = client;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.sleeper = sleeper;
        // 3일 넘게 안 늘면 알림 — 비공식 API 가 바뀐 신호
        Gauge.builder("steam.portrait.last.success", lastSuccessEpochSeconds, AtomicLong::get)
                .description("Steam 세로 표지 동기화 마지막 성공 시각 (epoch 초)")
                .register(meterRegistry);
    }

    /** 랭킹(04:00~) 뒤 · 변환(06:00) 앞의 빈 시간. */
    @Scheduled(cron = "0 10 5 * * *")
    public void scheduledSync() {
        if (!start(DAILY_CALLS)) log.warn("Steam 세로 표지 — 이전 실행이 아직 돌고 있어 오늘 예약 실행을 건너뛴다");
    }

    /** 비동기로 시작한다. 이미 돌고 있으면 false. */
    public boolean start(int calls) {
        int bounded = Math.max(1, Math.min(MAX_CALLS, calls));
        if (!running.compareAndSet(false, true)) return false;
        try {
            executor.execute(() -> {
                try {
                    run(bounded);
                } catch (Exception e) {
                    log.error("Steam 세로 표지 동기화 예외", e);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** 실행 본체 (잠금은 {@link #start} 가 잡는다). */
    Report run(int calls) {
        Instant started = clock.instant();
        Instant deadline = started.plus(TIME_LIMIT);
        List<Target> targets = store.targets(calls * SteamPortraitClient.MAX_BATCH);
        int made = 0, asked = 0, covers = 0, none = 0, missing = 0;
        boolean failed = false, timedOut = false;

        for (int from = 0; from < targets.size(); from += SteamPortraitClient.MAX_BATCH) {
            if (from > 0) {
                try {
                    sleeper.sleep(PAUSE_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed = true;
                    break;
                }
            }
            if (!clock.instant().isBefore(deadline)) {
                timedOut = true;
                break;
            }
            List<Target> batch = targets.subList(from, Math.min(from + SteamPortraitClient.MAX_BATCH, targets.size()));
            Map<Long, Portrait> answers;
            try {
                answers = client.fetch(batch.stream().map(Target::appId).toList());
            } catch (SteamPortraitException e) {
                log.warn("Steam 세로 표지 — 호출 실패로 이번 실행을 멈춘다(다음 실행이 이어 간다): {}", e.getMessage());
                failed = true;
                break;
            }
            made++;
            asked += batch.size();
            List<Checked> checked = new ArrayList<>(batch.size());
            for (Target t : batch) {
                Portrait p = answers.get(t.appId());
                if (p == null) {
                    missing++;               // 응답에 없음 — 확인 안 함
                    continue;
                }
                if (p.portraitUrl() != null) covers++;
                else none++;
                checked.add(new Checked(t.contentId(), p.portraitUrl()));
            }
            store.save(checked, clock.instant());
        }

        Report report = new Report(made, asked, covers, none, missing, failed, timedOut);
        meterRegistry.counter("steam.portrait.items", "result", "cover").increment(covers);
        meterRegistry.counter("steam.portrait.items", "result", "none").increment(none);
        meterRegistry.counter("steam.portrait.items", "result", "missing").increment(missing);
        meterRegistry.counter("steam.portrait.runs", "outcome", failed ? "failed" : "ok").increment();
        if (!failed) lastSuccessEpochSeconds.set(clock.instant().getEpochSecond());
        long seconds = Duration.between(started, clock.instant()).toSeconds();
        log.info("Steam 세로 표지 동기화 — 대상 {} · 호출 {} · 물음 {} · 표지 {} · 없음 {} · 응답에 없음 {} · {}{} · {}초",
                targets.size(), made, asked, covers, none, missing,
                failed ? "실패로 멈춤" : "완료", timedOut ? "(시간 상한)" : "", seconds);
        return report;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
