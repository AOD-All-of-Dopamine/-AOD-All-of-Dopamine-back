package com.example.crawler.contents.Webtoon.NaverWebtoon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 네이버 웹툰 정기 크롤링 스케줄러
 * - crawlerTaskExecutor 스레드풀 사용
 * - 비동기 실행으로 스케줄러 스레드 블로킹 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverWebtoonSchedulingService {

    private final NaverWebtoonService naverWebtoonService;

    /**
     * 매일 새벽 2시에 모든 요일의 웹툰 데이터 수집
     * - 전체 요일 크롤링 (월~일)
     * - 비동기 실행으로 즉시 반환
     */
    @Scheduled(cron = "0 0 2 * * *") // 매일 새벽 2시
    public void collectAllWeekdaysDaily() {
        log.info("🚀 [정기 스케줄] 네이버 웹툰 전체 요일 크롤링 시작");
        
        try {
            // 비동기로 실행 - crawlerTaskExecutor 사용
            naverWebtoonService.crawlAllWeekdays();
            log.info("✅ [정기 스케줄] 네이버 웹툰 크롤링 작업 트리거 완료 (비동기 실행 중)");
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] 네이버 웹툰 크롤링 트리거 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매주 일요일 새벽 3시에 완결 웹툰 수집
     * - 완결 작품은 변화가 적으므로 주 1회 업데이트
     * - 비동기 실행
     */
    @Scheduled(cron = "0 0 3 * * SUN") // 매주 일요일 새벽 3시
    public void collectFinishedWebtoonsWeekly() {
        log.info("🚀 [정기 스케줄] 네이버 웹툰 완결작 크롤링 시작");
        
        try {
            // 비동기로 실행 - crawlerTaskExecutor 사용 (최대 100페이지)
            naverWebtoonService.crawlFinishedWebtoons(100);
            log.info("✅ [정기 스케줄] 네이버 웹툰 완결작 크롤링 작업 트리거 완료 (비동기 실행 중)");
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] 네이버 웹툰 완결작 크롤링 트리거 실패: {}", e.getMessage(), e);
        }
    }
}


