package com.example.crawler.contents.webtoon.naverwebtoon;

import com.example.crawler.common.queue.CrawlJobProducer;
import com.example.crawler.common.queue.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 네이버 웹툰 JobProducer
 *
 * 대상 발견은 Fetcher(discoverAllWeekdays/discoverFinished)에 위임하고, 여기서는 큐 등록만 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverWebtoonJobProducer {

    private final CrawlJobProducer crawlJobProducer;
    private final NaverWebtoonFetcher naverWebtoonFetcher;

    /**
     * 네이버 웹툰 연재중 목록을 Job Queue에 등록합니다.
     *
     * 매일 새벽 2시 실행 (월~일 요일별 웹툰)
     */
    public void collectAllWeekdaysDaily() {
        log.info("📚 [Webtoon Producer] 네이버 웹툰 연재중 목록 수집 시작");

        try {
            List<String> webtoonIds = naverWebtoonFetcher.discoverAllWeekdays();

            if (!webtoonIds.isEmpty()) {
                int created = crawlJobProducer.createJobs(JobType.NAVER_WEBTOON, webtoonIds, 3);
                log.info("✅ [Webtoon Producer] 연재중 웹툰 {} 개 작업 생성 완료", created);
            } else {
                log.info("🔵 [Webtoon Producer] 연재중 웹툰 없음");
            }

        } catch (Exception e) {
            log.error("❌ [Webtoon Producer] 네이버 웹툰 목록 수집 중 오류 발생", e);
        }
    }

    /**
     * 네이버 웹툰 완결작 목록을 Job Queue에 등록합니다.
     *
     * 매주 일요일 새벽 3시 실행 (완결 웹툰은 변화 적음)
     */
    public void collectFinishedWebtoonsWeekly() {
        log.info("📚 [Webtoon Producer] 네이버 웹툰 완결작 목록 수집 시작");

        try {
            List<String> finishedIds = naverWebtoonFetcher.discoverFinished(100); // 최대 100페이지

            if (!finishedIds.isEmpty()) {
                int created = crawlJobProducer.createJobs(JobType.NAVER_WEBTOON_FINISHED, finishedIds, 2);
                log.info("✅ [Webtoon Producer] 완결 웹툰 {} 개 작업 생성 완료", created);
            } else {
                log.info("🔵 [Webtoon Producer] 완결 웹툰 없음");
            }

        } catch (Exception e) {
            log.error("❌ [Webtoon Producer] 네이버 완결 웹툰 목록 수집 중 오류 발생", e);
        }
    }
}
