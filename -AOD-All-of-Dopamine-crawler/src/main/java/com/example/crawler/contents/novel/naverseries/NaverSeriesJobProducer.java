package com.example.crawler.contents.novel.naverseries;

import com.example.crawler.common.queue.CrawlJobProducer;
import com.example.crawler.common.queue.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 네이버 시리즈 JobProducer
 *
 * 대상 발견은 Fetcher(discoverTargets)에 위임하고, 여기서는 큐 등록만 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverSeriesJobProducer {

    private final CrawlJobProducer crawlJobProducer;
    private final NaverSeriesFetcher naverSeriesFetcher;

    private static final String RECENT_NOVELS_URL = "https://series.naver.com/novel/recentList.series?page=";
    private static final String COMPLETED_NOVELS_URL = "https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=finished&page=";

    /**
     * 네이버 시리즈 신작 목록을 Job Queue에 등록합니다.
     * <p>
     * 매일 새벽 2시 실행 (최신 3페이지, 약 60개)
     */
    public void collectRecentNovelsDaily() {
        log.info("📖 [Novel Producer] 네이버 시리즈 신작 목록 수집 시작");

        try {
            List<String> novelIds = naverSeriesFetcher.discoverTargets(RECENT_NOVELS_URL, 3); // 최신 3페이지

            if (!novelIds.isEmpty()) {
                int created = crawlJobProducer.createJobs(JobType.NAVER_SERIES_NOVEL, novelIds, 3);
                log.info("✅ [Novel Producer] 네이버 시리즈 신작 {} 개 작업 생성 완료", created);
            } else {
                log.info("🔵 [Novel Producer] 네이버 시리즈 신작 없음");
            }

        } catch (Exception e) {
            log.error("❌ [Novel Producer] 네이버 시리즈 신작 목록 수집 중 오류 발생", e);
        }
    }

    /**
     * 네이버 시리즈 완결작 목록을 Job Queue에 등록합니다.
     * <p>
     * 매주 일요일 새벽 3시 실행 (최대 50페이지, 약 1000개)
     */
    public void collectCompletedNovelsWeekly() {
        log.info("📖 [Novel Producer] 네이버 시리즈 완결작 목록 수집 시작");

        try {
            List<String> completedIds = naverSeriesFetcher.discoverTargets(COMPLETED_NOVELS_URL, 50); // 최대 50페이지

            if (!completedIds.isEmpty()) {
                int created = crawlJobProducer.createJobs(JobType.NAVER_SERIES_NOVEL, completedIds, 2);
                log.info("✅ [Novel Producer] 네이버 시리즈 완결작 {} 개 작업 생성 완료", created);
            } else {
                log.info("🔵 [Novel Producer] 네이버 시리즈 완결작 없음");
            }

        } catch (Exception e) {
            log.error("❌ [Novel Producer] 네이버 시리즈 완결작 목록 수집 중 오류 발생", e);
        }
    }
}
