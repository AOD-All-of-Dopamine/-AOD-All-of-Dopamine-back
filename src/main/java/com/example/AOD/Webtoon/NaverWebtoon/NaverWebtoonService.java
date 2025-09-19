package com.example.AOD.Webtoon.NaverWebtoon;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 네이버 웹툰 크롤링 서비스
 * - 수동 트리거 전용 (자동 스케줄링 없음)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaverWebtoonService {

    private final NaverWebtoonCrawler naverWebtoonCrawler;

    /**
     * 모든 요일별 웹툰 크롤링 (수동 트리거)
     */
    @Async
    public void crawlAllWeekdays() {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("네이버 웹툰 전체 크롤링 작업 시작: {}", startTime);

        try {
            int totalSaved = naverWebtoonCrawler.crawlAllWeekdays();

            LocalDateTime endTime = LocalDateTime.now();
            log.info("네이버 웹툰 전체 크롤링 작업 완료. 소요 시간: {}초, {}개 웹툰 저장됨",
                    endTime.getSecond() - startTime.getSecond(), totalSaved);

        } catch (Exception e) {
            log.error("네이버 웹툰 전체 크롤링 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 특정 요일 웹툰 크롤링
     */
    @Async
    public void crawlWeekday(String weekday) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("네이버 웹툰 {} 요일 크롤링 작업 시작: {}", weekday, startTime);

        try {
            int saved = naverWebtoonCrawler.crawlWeekday(weekday);

            LocalDateTime endTime = LocalDateTime.now();
            log.info("네이버 웹툰 {} 요일 크롤링 작업 완료. 소요 시간: {}초, {}개 웹툰 저장됨",
                    weekday, endTime.getSecond() - startTime.getSecond(), saved);

        } catch (Exception e) {
            log.error("네이버 웹툰 {} 요일 크롤링 중 오류 발생: {}", weekday, e.getMessage(), e);
        }
    }

    /**
     * 완결 웹툰 크롤링 (페이지네이션)
     */
    @Async
    public void crawlFinishedWebtoons(int maxPages) {
        LocalDateTime startTime = LocalDateTime.now();
        log.info("네이버 웹툰 완결작 크롤링 작업 시작 (최대 {}페이지): {}", maxPages, startTime);

        try {
            int saved = naverWebtoonCrawler.crawlFinishedWebtoons(maxPages);

            LocalDateTime endTime = LocalDateTime.now();
            log.info("네이버 웹툰 완결작 크롤링 작업 완료. 소요 시간: {}초, {}개 웹툰 저장됨",
                    endTime.getSecond() - startTime.getSecond(), saved);

        } catch (Exception e) {
            log.error("네이버 웹툰 완결작 크롤링 중 오류 발생: {}", e.getMessage(), e);
        }
    }



    /**
     * 동기 버전 - 테스트나 즉시 실행용
     */
    public int crawlAllWeekdaysSync() throws Exception {
        return naverWebtoonCrawler.crawlAllWeekdays();
    }

    /**
     * 동기 버전 - 특정 요일 크롤링
     */
    public int crawlWeekdaySync(String weekday) throws Exception {
        return naverWebtoonCrawler.crawlWeekday(weekday);
    }

    /**
     * 동기 버전 - 완결 웹툰 크롤링
     */
    public int crawlFinishedWebtoonsSync(int maxPages) throws Exception {
        return naverWebtoonCrawler.crawlFinishedWebtoons(maxPages);
    }
}