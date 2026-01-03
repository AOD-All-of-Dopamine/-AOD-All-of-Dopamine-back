package com.example.crawler.contents.Novel.NaverSeriesNovel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 네이버 시리즈 정기 크롤링 스케줄러
 * - 신작: 매일 정기 수집 (recentList.series)
 * - 완결작: 주 1회 대규모 수집 (categoryProductList.series)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverSeriesSchedulingService {

    private final NaverSeriesCrawler naverSeriesCrawler;

    /**
     * 매일 새벽 2시에 네이버 시리즈 신작 수집
     * - 신작은 자주 업데이트되므로 매일 크롤링
     * - recentList.series 페이지 기준 (최신 3페이지, 약 60개)
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    public void collectRecentNovelsDaily() {
        log.info("🚀 [정기 스케줄] 네이버 시리즈 신작 크롤링 시작");
        
        try {
            String cookie = ""; // 쿠키 필요 시 설정
            int pages = 3; // 신작 최신 3페이지 (페이지당 20개, 총 60개)
            
            int saved = naverSeriesCrawler.crawlRecentNovels(cookie, pages);
            
            log.info("✅ [정기 스케줄] 네이버 시리즈 신작 크롤링 완료: {}개 저장", saved);
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] 네이버 시리즈 신작 크롤링 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매주 일요일 새벽 3시에 전체 완결작품 대규모 수집
     * - 완결작은 변화가 느리므로 주 1회 업데이트
     * - 최대 50페이지 (1000개 작품)
     */
    @Scheduled(cron = "0 0 3 * * SUN") // 매주 일요일 새벽 3시
    public void collectCompletedNovelsWeekly() {
        log.info("🚀 [정기 스케줄] 네이버 시리즈 완결작품 대규모 크롤링 시작");
        
        try {
            String cookie = "";
            int pages = 50; // 완결작품 50페이지 (페이지당 20개, 총 1000개)
            
            int saved = naverSeriesCrawler.crawlCompletedNovels(cookie, pages);
            
            log.info("✅ [정기 스케줄] 네이버 시리즈 완결작품 크롤링 완료: {}개 저장", saved);
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] 네이버 시리즈 완결작품 크롤링 실패: {}", e.getMessage(), e);
        }
    }
}


