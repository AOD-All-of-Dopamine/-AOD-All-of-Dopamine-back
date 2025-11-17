package com.example.AOD.contents.Novel.NaverSeriesNovel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 네이버 시리즈 정기 크롤링 스케줄러
 * - crawlerTaskExecutor 스레드풀 사용
 * - 비동기 실행으로 스케줄러 스레드 블로킹 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverSeriesSchedulingService {

    private final NaverSeriesCrawler naverSeriesCrawler;

    /**
     * 매주 화요일 새벽 2시에 네이버 시리즈 TOP100 수집
     * - 웹소설은 변화가 느리므로 주 1회 업데이트
     * - TOP100 일간 랭킹 기준
     */
    @Scheduled(cron = "0 0 2 * * TUE") // 매주 화요일 새벽 2시
    public void collectNaverSeriesWeekly() {
        log.info("🚀 [정기 스케줄] 네이버 시리즈 TOP100 크롤링 시작");
        
        try {
            String baseUrl = "https://series.naver.com/novel/top100List.series?rankingTypeCode=DAILY&categoryCode=ALL&page=";
            String cookie = ""; // 쿠키 필요 시 설정
            int pages = 5; // TOP100이므로 5페이지면 충분 (페이지당 20개)
            
            int saved = naverSeriesCrawler.crawlToRaw(baseUrl, cookie, pages);
            
            log.info("✅ [정기 스케줄] 네이버 시리즈 크롤링 완료: {}개 저장", saved);
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] 네이버 시리즈 크롤링 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매월 1일 새벽 3시에 전체 카테고리 수집
     * - 월 1회 대규모 수집
     * - 최대 50페이지 (1000개 작품)
     */
    @Scheduled(cron = "0 0 3 1 * *") // 매월 1일 새벽 3시
    public void collectAllCategoriesMonthly() {
        log.info("🚀 [정기 스케줄] 네이버 시리즈 전체 카테고리 대규모 크롤링 시작");
        
        try {
            String baseUrl = "https://series.naver.com/novel/top100List.series?rankingTypeCode=DAILY&categoryCode=ALL&page=";
            String cookie = "";
            int pages = 50; // 대규모 수집
            
            int saved = naverSeriesCrawler.crawlToRaw(baseUrl, cookie, pages);
            
            log.info("✅ [정기 스케줄] 네이버 시리즈 대규모 크롤링 완료: {}개 저장", saved);
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] 네이버 시리즈 대규모 크롤링 실패: {}", e.getMessage(), e);
        }
    }
}
