package com.example.AOD.game.steam.service;

import com.example.AOD.game.steam.fetcher.SteamApiFetcher;
import com.example.AOD.game.steam.processor.SteamPayloadProcessor;
import com.example.AOD.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamCrawlService {

    private final SteamApiFetcher steamApiFetcher;
    private final CollectorService collectorService;
    private final SteamPayloadProcessor payloadProcessor;

    @Async
    public void collectAllGamesInBatches() {
        log.info("Steam 전체 게임 데이터 자동 분할 수집을 시작합니다.");
        List<Map<String, Object>> gameApps = steamApiFetcher.fetchGameApps();
        if (gameApps.isEmpty()) {
            log.warn("Steam 게임 앱 목록이 비어있어 전체 수집을 중단합니다.");
            return;
        }

        final int batchSize = 1000;
        int totalApps = gameApps.size();

        for (int i = 0; i < totalApps; i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, totalApps);
            log.info("==> Steam 자동 수집 배치 실행: {} / {} (인덱스 {}부터 {}까지)", (start / batchSize) + 1, (totalApps / batchSize) + 1, start, end);

            List<Map<String, Object>> batch = gameApps.subList(start, end);
            collectGamesFromList(batch);

            try {
                if (end < totalApps) {
                    log.info("Batch {} 완료. 1분간 대기합니다...", (start / batchSize) + 1);
                    TimeUnit.MINUTES.sleep(1);
                }
            } catch (InterruptedException e) {
                log.error("Steam 배치 수집 작업 중 대기 시간 인터럽트 발생.", e);
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Steam 전체 게임 데이터 자동 분할 수집이 완료되었습니다.");
    }

    @Async
    public void collectAllGamesInRange(int start, int end) {
        log.info("Steam 게임 데이터 범위 지정 수집 시작. 요청 범위: {}부터 {}까지", start, end);
        List<Map<String, Object>> gameApps = steamApiFetcher.fetchGameApps();

        if (gameApps.isEmpty()) {
            log.warn("Steam 게임 앱 목록을 가져오지 못해 수집을 중단합니다.");
            return;
        }

        int effectiveStart = Math.max(0, start);
        int effectiveEnd = Math.min(end, gameApps.size());

        List<Map<String, Object>> range = gameApps.subList(effectiveStart, effectiveEnd);
        collectGamesFromList(range);
    }

    private void collectGamesFromList(List<Map<String, Object>> appList) {
        int collectedCount = 0;
        for (Map<String, Object> app : appList) {
            Long appId = ((Number) app.get("appid")).longValue();
            String appName = (String) app.get("name");

            if (appName == null || appName.isBlank()) {
                continue;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(500);

                Map<String, Object> gameDetails = steamApiFetcher.fetchGameDetails(appId);

                if (gameDetails != null && "game".equals(gameDetails.get("type"))) {
                    Map<String, Object> processedDetails = payloadProcessor.process(gameDetails);
                    collectorService.saveRaw(
                            "Steam",
                            "GAME",
                            processedDetails,
                            String.valueOf(appId),
                            "https://store.steampowered.com/app/" + appId
                    );
                    collectedCount++;
                    log.info("Steam 게임 수집 성공: {} (ID: {})", appName, appId);
                } else {
                    log.debug("Skipping AppID {} ({}): Not a game or details unavailable.", appId, appName);
                }

            } catch (InterruptedException e) {
                log.error("Steam 수집 작업이 중단되었습니다.", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Steam 게임 ID {} ({}) 처리 중 오류 발생: {}", appId, appName, e.getMessage());
            }
        }
        log.info("Steam 게임 데이터 수집 완료. 현재 작업에서 총 {}개의 유효한 게임을 수집했습니다.", collectedCount);
    }
}