package com.example.crawler.game.steam.service;

import com.example.crawler.crawl.CrawlPipeline;
import com.example.crawler.game.steam.fetcher.SteamApiFetcher;
import com.example.crawler.game.steam.source.SteamGameSource;
import com.example.crawler.util.InterruptibleSleep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamCrawlService {

    private final SteamApiFetcher steamApiFetcher;
    private final CrawlPipeline crawlPipeline;
    private final SteamGameSource steamGameSource;

    @Async("crawlerTaskExecutor")
    public CompletableFuture<Integer> collectAllGamesInBatches() {
        log.info("Steam 전체 게임 데이터 자동 분할 수집을 시작합니다.");
        List<Map<String, Object>> gameApps = steamApiFetcher.fetchGameApps();
        if (gameApps.isEmpty()) {
            log.warn("Steam 게임 앱 목록이 비어있어 전체 수집을 중단합니다.");
            return CompletableFuture.completedFuture(0);
        }

        final int batchSize = 1000;
        int totalApps = gameApps.size();
        int totalCollected = 0;

        for (int i = 0; i < totalApps; i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, totalApps);
            log.info("==> Steam 자동 수집 배치 실행: {} / {} (인덱스 {}부터 {}까지)", (start / batchSize) + 1,
                    (totalApps / batchSize) + 1, start, end);

            List<Map<String, Object>> batch = gameApps.subList(start, end);
            int batchCount = collectGamesFromList(batch);
            totalCollected += batchCount;

            if (end < totalApps) {
                log.info("Batch {} 완료. 1분간 대기합니다...", (start / batchSize) + 1);
                if (!InterruptibleSleep.sleep(1, TimeUnit.MINUTES)) {
                    log.info("Steam 배치 수집 인터럽트 발생, 작업 중단 (총 {}개 수집)", totalCollected);
                    return CompletableFuture.completedFuture(totalCollected);
                }
            }
        }
        log.info("Steam 전체 게임 데이터 자동 분할 수집이 완료되었습니다. (총 {}개 수집)", totalCollected);
        return CompletableFuture.completedFuture(totalCollected);
    }

    @Async("crawlerTaskExecutor")
    public CompletableFuture<Integer> collectAllGamesInRange(int start, int end) {
        log.info("Steam 게임 데이터 범위 지정 수집 시작. 요청 범위: {}부터 {}까지", start, end);
        List<Map<String, Object>> gameApps = steamApiFetcher.fetchGameApps();

        if (gameApps.isEmpty()) {
            log.warn("Steam 게임 앱 목록을 가져오지 못해 수집을 중단합니다.");
            return CompletableFuture.completedFuture(0);
        }

        int effectiveStart = Math.max(0, start);
        int effectiveEnd = Math.min(end, gameApps.size());

        List<Map<String, Object>> range = gameApps.subList(effectiveStart, effectiveEnd);
        int collected = collectGamesFromList(range);
        return CompletableFuture.completedFuture(collected);
    }

    private int collectGamesFromList(List<Map<String, Object>> appList) {
        int collectedCount = 0;
        for (Map<String, Object> app : appList) {
            Long appId = ((Number) app.get("appid")).longValue();
            String appName = (String) app.get("name");

            if (appName == null || appName.isBlank()) {
                continue;
            }

            // 단일 경로(CrawlPipeline + SteamGameSource)로 통일 — fetch/process/save 중복 제거
            if (crawlPipeline.run(steamGameSource, String.valueOf(appId)).isSuccess()) {
                collectedCount++;
            }
        }
        log.info("Steam 게임 데이터 수집 완료. 현재 작업에서 총 {}개의 유효한 게임을 수집했습니다.", collectedCount);
        return collectedCount;
    }
}
