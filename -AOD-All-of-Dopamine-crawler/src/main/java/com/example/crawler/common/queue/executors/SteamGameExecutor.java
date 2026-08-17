package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.game.steam.SteamFetcher;
import com.example.crawler.contents.game.steam.SteamPayloadProcessor;
import com.example.crawler.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Steam 게임 크롤링 Executor
 * 표준형: Fetcher 상세 호출 → 성인 게임 스킵 → PayloadProcessor.process
 *        → 리뷰 집계 병합(best-effort) → CollectorService.saveRaw
 * (레이트리밋은 SteamFetcher 내부의 SteamRateLimiter가 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamGameExecutor implements JobExecutor {

    /** 성인 판정 기준: appdetails required_age >= 18 (2026-08 Steam 정제 ① — 수집 단계 차단) */
    private static final int ADULT_REQUIRED_AGE = 18;

    private final SteamFetcher steamFetcher;
    private final SteamPayloadProcessor payloadProcessor;
    private final CollectorService collectorService;

    /** 성인 스킵 누적 카운트 (로그·테스트용) */
    private final AtomicLong adultSkipCount = new AtomicLong();

    @Override
    public JobType getJobType() {
        return JobType.STEAM_GAME;
    }

    @Override
    public boolean execute(String targetId) {
        return collectGameByAppId(Long.parseLong(targetId));
    }

    /**
     * 특정 AppID의 Steam 게임 상세 정보를 수집하여 저장합니다.
     *
     * @param appId Steam 게임의 고유 ID
     * @return 수집 성공 여부
     */
    private boolean collectGameByAppId(Long appId) {
        log.info("Steam 게임 AppID {} 데이터 수집 시작", appId);

        try {
            Map<String, Object> gameDetails = steamFetcher.fetchGameDetails(appId);

            if (gameDetails == null) {
                log.warn("AppID {}의 상세 정보를 가져올 수 없습니다.", appId);
                return false;
            }

            if (!"game".equals(gameDetails.get("type"))) {
                log.warn("AppID {}는 게임이 아닙니다. Type: {}", appId, gameDetails.get("type"));
                return false;
            }

            // 성인 게임은 saveRaw 전에 스킵 — DB에 아예 담지 않는다 (기존분 차단은 contents.is_adult가 담당)
            int requiredAge = parseRequiredAge(gameDetails.get("required_age"));
            if (requiredAge >= ADULT_REQUIRED_AGE) {
                log.info("성인 게임 수집 스킵: {} (AppID: {}, required_age: {}) — 누적 {}건",
                        gameDetails.get("name"), appId, requiredAge, adultSkipCount.incrementAndGet());
                return true; // 의도된 스킵 = 작업 성공 (false면 실패로 재시도됨)
            }

            String appName = (String) gameDetails.get("name");
            Map<String, Object> processedDetails = payloadProcessor.process(gameDetails);
            Map<String, Object> reviewSummary = steamFetcher.fetchReviewSummary(appId);
            if (reviewSummary != null) processedDetails.put("review_summary", reviewSummary);

            collectorService.saveRaw(
                    "Steam",
                    "GAME",
                    processedDetails,
                    String.valueOf(appId),
                    "https://store.steampowered.com/app/" + appId);

            log.info("Steam 게임 수집 성공: {} (AppID: {})", appName, appId);
            return true;

        } catch (Exception e) {
            log.error("Steam 게임 AppID {} 처리 중 오류 발생: {}", appId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * appdetails의 required_age 파싱 — 숫자(0)·문자열("18", "18+") 혼재 응답을 흡수.
     * 파싱 불가·부재는 0(전체이용가) 취급.
     */
    static int parseRequiredAge(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            String digits = s.replaceAll("\\D", "");
            if (!digits.isEmpty()) {
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                    // 자릿수 초과 등 — 0 취급
                }
            }
        }
        return 0;
    }

    /** 성인 스킵 누적 건수 (테스트·운영 확인용) */
    public long getAdultSkipCount() {
        return adultSkipCount.get();
    }

    @Override
    public long getAverageExecutionTime() {
        return 2000; // 게임당 API 2회 호출 (appdetails + appreviews 리뷰 집계) — 평균 2초
    }
}
