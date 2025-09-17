package com.example.AOD.game.steam.service;

import com.example.AOD.game.steam.fetcher.SteamApiFetcher;
import com.example.AOD.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamCrawlService {

    private final SteamApiFetcher steamApiFetcher;
    private final CollectorService collectorService;

    /**
     * (신규) 모든 Steam 앱의 기본 목록을 조회하여 반환합니다.
     * @return 전체 앱 목록
     */
    public List<Map<String, Object>> fetchAllGamesList() {
        return steamApiFetcher.fetchAllApps();
    }

    /**
     * (신규) 모든 Steam 게임 정보를 1000개씩 나누어 순차적으로 수집합니다.
     */
    @Async
    public void collectAllGamesInBatches() {
        log.info("Steam 전체 게임 데이터 자동 분할 수집을 시작합니다.");
        List<Map<String, Object>> allApps = fetchAllGamesList();
        if (allApps.isEmpty()) {
            log.warn("Steam 앱 목록이 비어있어 전체 수집을 중단합니다.");
            return;
        }

        final int batchSize = 1000;
        int totalApps = allApps.size();

        for (int i = 0; i < totalApps; i += batchSize) {
            int start = i;
            int end = Math.min(i + batchSize, totalApps);
            log.info("==> Steam 자동 수집 배치 실행: {} / {} (인덱스 {}부터 {}까지)", (start / batchSize) + 1, (totalApps / batchSize) + 1, start, end);
            collectGamesByRange(start, end);

            try {
                // 각 배치 작업 후 API 제한을 피하기 위해 잠시 대기
                TimeUnit.MINUTES.sleep(1);
            } catch (InterruptedException e) {
                log.error("Steam 배치 수집 작업 중 대기 시간 인터럽트 발생.", e);
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Steam 전체 게임 데이터 자동 분할 수집이 완료되었습니다.");
    }


    /**
     * (기존) Steam 게임 정보를 지정된 인덱스 범위만큼 수집하여 raw_items 테이블에 저장합니다.
     * @param start 수집 시작 인덱스
     * @param end   수집 종료 인덱스
     */
    @Async
    public void collectGamesByRange(int start, int end) {
        log.info("Steam 게임 데이터 수집 시작. 요청 범위: {}부터 {}까지", start, end);
        List<Map<String, Object>> allApps = steamApiFetcher.fetchAllApps();

        if (allApps.isEmpty()) {
            log.warn("Steam 앱 목록을 가져오지 못해 수집을 중단합니다.");
            return;
        }

        int effectiveStart = Math.max(0, start);
        int effectiveEnd = Math.min(end, allApps.size());
        int collectedCount = 0;

        log.info("전체 {}개 앱 중, 유효 범위 {}부터 {}까지 수집을 진행합니다.", allApps.size(), effectiveStart, effectiveEnd);

        for (int i = effectiveStart; i < effectiveEnd; i++) {
            Map<String, Object> app = allApps.get(i);
            Long appId = ((Number) app.get("appid")).longValue();
            String appName = (String) app.get("name");

            if (appName == null || appName.isBlank()) {
                continue;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(500);

                Map<String, Object> gameDetails = steamApiFetcher.fetchGameDetails(appId);

                if (gameDetails != null && "game".equals(gameDetails.get("type"))) {
                    collectorService.saveRaw(
                            "Steam",
                            "GAME",
                            gameDetails,
                            String.valueOf(appId),
                            "https://store.steampowered.com/app/" + appId
                    );
                    collectedCount++;
                    log.info("[{}/{}] Steam 게임 수집 성공: {} (ID: {})", (i + 1), allApps.size(), appName, appId);
                }

            } catch (InterruptedException e) {
                log.error("Steam 수집 작업이 중단되었습니다.", e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Steam 게임 ID {} 처리 중 오류 발생: {}", appId, e.getMessage());
            }
        }
        log.info("Steam 게임 데이터 수집 완료. 요청 범위 내에서 총 {}개의 유효한 게임을 수집했습니다.", collectedCount);
    }
}