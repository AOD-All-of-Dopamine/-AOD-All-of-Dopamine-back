package com.example.crawler.contents.Webtoon.NaverWebtoon;


import com.example.crawler.monitoring.CustomMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 네이버 웹툰 크롤링 서비스
 * - 수동 트리거 전용 (자동 스케줄링 없음)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaverWebtoonService {

    private final NaverWebtoonCrawler naverWebtoonCrawler;
    private final CustomMetrics customMetrics;

    /**
     * 모든 요일별 웹툰 크롤링 (수동 트리거)
     */
    @Async("crawlerTaskExecutor")
    public CompletableFuture<Integer> crawlAllWeekdays() {
        String platform = "NaverWebtoon-All";
        Timer.Sample sample = customMetrics.startTimer();

        LocalDateTime startTime = LocalDateTime.now();
        log.info("네이버 웹툰 전체 크롤링 작업 시작: {}", startTime);

        try {
            int totalSaved = naverWebtoonCrawler.crawlAllWeekdays();

            customMetrics.recordCrawlerSuccess(platform);
            customMetrics.recordItemsProcessed(platform, totalSaved);

            LocalDateTime endTime = LocalDateTime.now();
            log.info("네이버 웹툰 전체 크롤링 작업 완료. 소요 시간: {}초, {}개 웹툰 저장됨",
                    endTime.getSecond() - startTime.getSecond(), totalSaved);

            return CompletableFuture.completedFuture(totalSaved);

        } catch (Exception e) {
            customMetrics.recordCrawlerFailure(platform, e.getClass().getSimpleName());
            log.error("네이버 웹툰 전체 크롤링 중 오류 발생: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        } finally {
            customMetrics.recordDuration(sample, platform);
            // ThreadLocal 자원 정리 보장
            cleanupSeleniumResources();
        }
    }

    /**
     * 특정 요일 웹툰 크롤링
     */
    @Async("crawlerTaskExecutor")
    public CompletableFuture<Integer> crawlWeekday(String weekday) {
        String platform = "NaverWebtoon-" + weekday;
        Timer.Sample sample = customMetrics.startTimer();

        LocalDateTime startTime = LocalDateTime.now();
        log.info("네이버 웹툰 {} 요일 크롤링 작업 시작: {}", weekday, startTime);

        try {
            int saved = naverWebtoonCrawler.crawlWeekday(weekday);

            customMetrics.recordCrawlerSuccess(platform);
            customMetrics.recordItemsProcessed(platform, saved);

            LocalDateTime endTime = LocalDateTime.now();
            log.info("네이버 웹툰 {} 요일 크롤링 작업 완료. 소요 시간: {}초, {}개 웹툰 저장됨",
                    weekday, endTime.getSecond() - startTime.getSecond(), saved);

            return CompletableFuture.completedFuture(saved);

        } catch (Exception e) {
            customMetrics.recordCrawlerFailure(platform, e.getClass().getSimpleName());
            log.error("네이버 웹툰 {} 요일 크롤링 중 오류 발생: {}", weekday, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
        finally {
            customMetrics.recordDuration(sample, platform);
            // ThreadLocal 자원 정리 보장
            cleanupSeleniumResources();
        }
    }

    /**
     * 완결 웹툰 크롤링 (페이지네이션)
     */
    @Async("crawlerTaskExecutor")
    public CompletableFuture<Integer> crawlFinishedWebtoons(int maxPages) {
        String platform = "NaverWebtoon-Finished";
        Timer.Sample sample = customMetrics.startTimer();

        LocalDateTime startTime = LocalDateTime.now();
        log.info("네이버 웹툰 완결작 크롤링 작업 시작 (최대 {}페이지): {}", maxPages, startTime);

        try {
            int saved = naverWebtoonCrawler.crawlFinishedWebtoons(maxPages);

            customMetrics.recordCrawlerSuccess(platform);
            customMetrics.recordItemsProcessed(platform, saved);

            LocalDateTime endTime = LocalDateTime.now();
            log.info("네이버 웹툰 완결작 크롤링 작업 완료. 소요 시간: {}초, {}개 웹툰 저장됨",
                    endTime.getSecond() - startTime.getSecond(), saved);

            return CompletableFuture.completedFuture(saved);

        } catch (Exception e) {
            customMetrics.recordCrawlerFailure(platform, e.getClass().getSimpleName());
            log.error("네이버 웹툰 완결작 크롤링 중 오류 발생: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        } finally {
            customMetrics.recordDuration(sample, platform);
            // ThreadLocal 자원 정리 보장
            cleanupSeleniumResources();
        }
    }

    /**
     * Selenium ThreadLocal 자원 정리
     */
    private void cleanupSeleniumResources() {
        try {
            WebtoonPageParser parser = naverWebtoonCrawler.getPageParser();
            if (parser instanceof NaverWebtoonSeleniumPageParser) {
                ((NaverWebtoonSeleniumPageParser) parser).cleanup();
                log.debug("ThreadLocal WebDriver 자원 정리 완료");
            }
        } catch (Exception e) {
            log.warn("ThreadLocal 자원 정리 중 오류 발생: {}", e.getMessage());
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

