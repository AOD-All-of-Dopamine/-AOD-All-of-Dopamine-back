package com.example.crawler.ranking.scheduler;

import com.example.crawler.ranking.service.RankingCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 랭킹 정기 크롤링 스케줄러
 * 
 * 각 플랫폼의 랭킹을 매일 새벽 4시부터 2분 간격으로 순차 갱신합니다.
 * - 04:00 네이버 웹툰 (오늘 요일 기준)
 * - 04:02 네이버 시리즈 (웹소설 일간)
 * - 04:04 Steam (최고 판매)
 * - 04:06 TMDB (인기 영화 & TV 쇼)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingScheduler {

    private final RankingCrawlerService rankingCrawlerService;

    /**
     * 네이버 웹툰 랭킹 - 매일 04:00
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void updateNaverWebtoonRanking() {
        executeWithLogging("네이버 웹툰", rankingCrawlerService::crawlNaverWebtoonRanking);
    }

    /**
     * 네이버 시리즈 랭킹 - 매일 04:02
     */
    @Scheduled(cron = "0 2 4 * * *")
    public void updateNaverSeriesRanking() {
        executeWithLogging("네이버 시리즈", rankingCrawlerService::crawlNaverSeriesRanking);
    }

    /**
     * Steam 랭킹 - 매일 04:04
     */
    @Scheduled(cron = "0 4 4 * * *")
    public void updateSteamRanking() {
        executeWithLogging("Steam", rankingCrawlerService::crawlSteamRanking);
    }

    /**
     * TMDB 랭킹 (영화 + TV) - 매일 04:06
     */
    @Scheduled(cron = "0 6 4 * * *")
    public void updateTmdbRanking() {
        executeWithLogging("TMDB", rankingCrawlerService::crawlTmdbRanking);
    }

    /**
     * 모든 플랫폼의 랭킹을 매일 새벽 4시에 자동 갱신 (전체 일괄)
     */
    // @Scheduled(cron = "0 0 4 * * *")
    // public void updateAllRankingsDaily() {
    //     log.info("🚀 [정기 스케줄] 전체 플랫폼 랭킹 크롤링 시작");
    //     long startTime = System.currentTimeMillis();
    //     try {
    //         rankingCrawlerService.crawlAndGetAllRankings();
    //         long duration = (System.currentTimeMillis() - startTime) / 1000;
    //         log.info("✅ [정기 스케줄] 전체 플랫폼 랭킹 크롤링 완료 (소요 시간: {}초)", duration);
    //     } catch (Exception e) {
    //         log.error("❌ [정기 스케줄] 랭킹 크롤링 실패: {}", e.getMessage(), e);
    //     }
    // }

    private void executeWithLogging(String platform, Runnable task) {
        log.info("🚀 [정기 스케줄] {} 랭킹 크롤링 시작", platform);
        long startTime = System.currentTimeMillis();

        try {
            task.run();
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("✅ [정기 스케줄] {} 랭킹 크롤링 완료 (소요 시간: {}초)", platform, duration);
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] {} 랭킹 크롤링 실패: {}", platform, e.getMessage(), e);
        }
    }
}
