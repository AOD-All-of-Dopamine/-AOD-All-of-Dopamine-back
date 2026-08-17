package com.example.crawler.common.queue.executors;

import com.example.crawler.common.queue.JobExecutor;
import com.example.crawler.common.queue.JobType;
import com.example.crawler.contents.game.steam.SteamFetcher;
import com.example.crawler.contents.game.steam.SteamPayloadProcessor;
import com.example.crawler.ingest.CollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Steam 게임 크롤링 Executor
 * 표준형: Fetcher 상세 호출 → 성적 콘텐츠 스킵 → PayloadProcessor.process
 *        → 리뷰 집계 병합(best-effort) → CollectorService.saveRaw
 * (레이트리밋은 SteamFetcher 내부의 SteamRateLimiter가 처리)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamGameExecutor implements JobExecutor {

    /**
     * 성인 판정 기준 (2026-08 Steam 정제 ① — 수집 단계 차단):
     * appdetails content_descriptors.ids에 3(Adult Only Sexual Content) 또는
     * 4(Frequent Nudity or Sexual Content) = 성적 콘텐츠만 제외.
     * required_age 기준은 기각 — 실측 상 폭력성 18금(GTA류)만 걸러 정반대로 작동했다.
     */
    static final Set<Integer> SEXUAL_CONTENT_DESCRIPTOR_IDS = Set.of(3, 4);

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

            // 성적 콘텐츠 게임은 saveRaw 전에 스킵 — DB에 아예 담지 않는다
            // (기존분 차단은 contents.is_adult + 부팅 reconcile이 담당)
            if (hasSexualContentDescriptor(gameDetails.get("content_descriptors"))) {
                log.info("성적 콘텐츠 게임 수집 스킵: {} (AppID: {}) — 누적 {}건",
                        gameDetails.get("name"), appId, adultSkipCount.incrementAndGet());
                // 의도된 스킵 = 작업 성공 — false면 markAsFailed로 maxRetries까지 재클레임 후 영구 FAILED
                return true;
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
     * appdetails content_descriptors({ids:[...], notes:...})에 성적 콘텐츠 디스크립터(3/4)가 있는지.
     * ids 원소는 숫자·문자열 혼재 가능성을 흡수. 구조가 다르거나 부재면 false(비성인 취급).
     */
    static boolean hasSexualContentDescriptor(Object contentDescriptors) {
        if (!(contentDescriptors instanceof Map<?, ?> m)) return false;
        if (!(m.get("ids") instanceof List<?> ids)) return false;
        for (Object v : ids) {
            Integer id = null;
            if (v instanceof Number n) {
                id = n.intValue();
            } else if (v != null) {
                try {
                    id = Integer.parseInt(v.toString().trim());
                } catch (NumberFormatException ignored) {
                    // 알 수 없는 토큰 — 무시
                }
            }
            if (id != null && SEXUAL_CONTENT_DESCRIPTOR_IDS.contains(id)) return true;
        }
        return false;
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
