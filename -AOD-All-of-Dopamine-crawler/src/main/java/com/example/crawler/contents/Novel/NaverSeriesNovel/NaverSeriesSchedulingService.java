package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.example.crawler.common.queue.CrawlJobProducer;
import com.example.crawler.common.queue.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 네이버 시리즈 크롤링 스케줄링 서비스
 * 
 * Job Queue 기반으로 작업을 생성합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverSeriesSchedulingService {

    private final CrawlJobProducer crawlJobProducer;

    private static final String RECENT_NOVELS_URL = "https://series.naver.com/novel/recentList.series?page=";
    private static final String COMPLETED_NOVELS_URL = "https://series.naver.com/novel/categoryProductList.series?categoryTypeCode=finished&page=";
    private static final Pattern PRODUCT_NO_PATTERN = Pattern.compile("productNo=(\\d+)");

    /**
     * 네이버 시리즈 신작 목록을 Job Queue에 등록합니다.
     * <p>
     * 매일 새벽 2시 실행 (최신 3페이지, 약 60개)
     */
    public void collectRecentNovelsDaily() {
        log.info("📖 [Novel Producer] 네이버 시리즈 신작 목록 수집 시작");

        try {
            List<String> novelIds = fetchNovelIdsByUrl(RECENT_NOVELS_URL, 3); // 최신 3페이지

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
            List<String> completedIds = fetchNovelIdsByUrl(COMPLETED_NOVELS_URL, 50); // 최대 50페이지

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

    /**
     * 네이버 시리즈 목록 페이지로부터 소설 ID 목록 가져오기 (Jsoup HTML 파싱)
     * — 스케줄러와 AdminTestController가 공용으로 사용
     */
    public List<String> fetchNovelIdsByUrl(String baseUrl, int maxPages) {
        Set<String> novelIds = new LinkedHashSet<>();

        for (int page = 1; page <= maxPages; page++) {
            try {
                String pageUrl = baseUrl + page;
                log.debug("[Novel Producer] 페이지 {} 크롤링 중: {}", page, pageUrl);

                Document doc = Jsoup.connect(pageUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();

                // productNo 파라미터가 있는 링크에서 ID 추출
                int foundOnPage = 0;
                for (Element a : doc.select("a[href*='/novel/detail.series'][href*='productNo=']")) {
                    String href = a.attr("href");
                    Matcher matcher = PRODUCT_NO_PATTERN.matcher(href);
                    if (matcher.find()) {
                        String productNo = matcher.group(1);
                        if (novelIds.add(productNo)) {
                            foundOnPage++;
                        }
                    }
                }

                // productNo가 없는 경우 전체 detail 링크에서도 시도
                if (foundOnPage == 0) {
                    for (Element a : doc.select("a[href*='/novel/detail.series']")) {
                        String href = a.attr("href");
                        Matcher matcher = PRODUCT_NO_PATTERN.matcher(href);
                        if (matcher.find()) {
                            String productNo = matcher.group(1);
                            if (novelIds.add(productNo)) {
                                foundOnPage++;
                            }
                        }
                    }
                }

                log.debug("[Novel Producer] 페이지 {}: {} 개 발견 (총 {}개)", page, foundOnPage, novelIds.size());

                if (foundOnPage == 0) {
                    log.debug("[Novel Producer] 페이지 {} 소설 없음, 종료", page);
                    break;
                }

                // 요청 제한 방지
                Thread.sleep(500);

            } catch (Exception e) {
                log.error("[Novel Producer] 페이지 {} 가져오기 실패", page, e);
                break;
            }
        }

        return new ArrayList<>(novelIds);
    }
}

