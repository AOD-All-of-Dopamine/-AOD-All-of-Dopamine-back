package com.example.crawler.ranking.scheduler;

import com.example.crawler.ranking.service.RankingCrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 랭킹 정기 크롤링 스케줄러
 * 
 * 모든 플랫폼의 랭킹을 매일 자동으로 갱신합니다.
 * - 네이버 웹툰 (오늘 요일 기준)
 * - 네이버 시리즈 (웹소설 일간)
 * - Steam (최고 판매)
 * - TMDB (인기 영화 & TV 쇼)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingScheduler {

    private final RankingCrawlerService rankingCrawlerService;

    /**
     * 모든 플랫폼의 랭킹을 매일 새벽 4시에 자동 갱신
     * 
     * 스케줄 타임라인:
     * - 01:00 - TMDB 신규 콘텐츠
     * - 02:00 - 네이버 웹툰
     * - 03:00 - 네이버 웹툰 완결작 (일요일) / Steam 게임 (목요일)
     * - 04:00 - 랭킹 크롤링 (매일) ← 여기
     * - 06:00 - Transform (매일)
     * - 07:00 - Transform 주간 배치 (일요일)
     */
    @Scheduled(cron = "0 0 15 * * *")
    public void updateAllRankingsDaily() {
        log.info("🚀 [정기 스케줄] 전체 플랫폼 랭킹 크롤링 시작");
        
        long startTime = System.currentTimeMillis();
        
        try {
            rankingCrawlerService.crawlAndGetAllRankings();
            
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("✅ [정기 스케줄] 전체 플랫폼 랭킹 크롤링 완료 (소요 시간: {}초)", duration);
            
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] 랭킹 크롤링 실패: {}", e.getMessage(), e);
        }
    }
}
