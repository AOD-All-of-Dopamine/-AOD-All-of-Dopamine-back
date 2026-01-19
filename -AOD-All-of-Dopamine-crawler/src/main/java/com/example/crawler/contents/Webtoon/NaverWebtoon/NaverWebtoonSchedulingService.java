package com.example.crawler.contents.Webtoon.NaverWebtoon;

import com.example.crawler.common.queue.CrawlJobProducer;
import com.example.crawler.common.queue.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 네이버 웹툰 크롤링 스케줄링 서비스
 * 
 * Job Queue 기반으로 작업을 생성합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NaverWebtoonSchedulingService {

    private final CrawlJobProducer crawlJobProducer;

    private static final String BASE_WEEKDAY_URL = "https://m.comic.naver.com/webtoon/weekday?week=";
    private static final String BASE_FINISH_URL = "https://m.comic.naver.com/webtoon/finish";
    private static final String[] WEEKDAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    /**
     * 네이버 웹툰 연재중 목록을 Job Queue에 등록합니다.
     * 
     * 매일 새벽 2시 실행 (월~일 요일별 웹툰)
     */
    public void collectAllWeekdaysDaily() {
        log.info("📚 [Webtoon Producer] 네이버 웹툰 연재중 목록 수집 시작");
        
        try {
            List<String> webtoonIds = new ArrayList<>();
            
            // 모든 요일별 웹툰 ID 수집
            for (String weekday : WEEKDAYS) {
                List<String> dailyIds = fetchWebtoonIdsByWeekday(weekday);
                webtoonIds.addAll(dailyIds);
                log.debug("[Webtoon] {} 요일: {} 개 발견", weekday, dailyIds.size());
            }
            
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
            List<String> finishedIds = fetchFinishedWebtoonIds(100); // 최대 100페이지
            
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

    /**
     * 특정 요일의 웹툰 ID 목록 가져오기
     */
    private List<String> fetchWebtoonIdsByWeekday(String weekday) {
        List<String> webtoonIds = new ArrayList<>();
        
        try {
            String url = BASE_WEEKDAY_URL + weekday;
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();
            
            // 모바일 페이지에서 titleId 추출
            Elements webtoonLinks = doc.select("a[href*=titleId]");
            
            for (Element link : webtoonLinks) {
                String href = link.attr("href");
                String titleId = extractTitleId(href);
                if (titleId != null && !webtoonIds.contains(titleId)) {
                    webtoonIds.add(titleId);
                }
            }
            
        } catch (Exception e) {
            log.error("[Webtoon] {} 요일 목록 가져오기 실패", weekday, e);
        }
        
        return webtoonIds;
    }

    /**
     * 완결 웹툰 ID 목록 가져오기 (페이지네이션)
     */
    private List<String> fetchFinishedWebtoonIds(int maxPages) {
        List<String> webtoonIds = new ArrayList<>();
        
        for (int page = 1; page <= maxPages; page++) {
            try {
                String url = BASE_FINISH_URL + "?page=" + page;
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0")
                        .timeout(10000)
                        .get();
                
                Elements webtoonLinks = doc.select("a[href*=titleId]");
                
                if (webtoonLinks.isEmpty()) {
                    log.debug("[Webtoon] 완결작 페이지 {} 데이터 없음, 종료", page);
                    break;
                }
                
                for (Element link : webtoonLinks) {
                    String href = link.attr("href");
                    String titleId = extractTitleId(href);
                    if (titleId != null && !webtoonIds.contains(titleId)) {
                        webtoonIds.add(titleId);
                    }
                }
                
                log.debug("[Webtoon] 완결작 페이지 {}: {} 개 발견", page, webtoonLinks.size());
                
                // 요청 제한 방지
                Thread.sleep(500);
                
            } catch (Exception e) {
                log.error("[Webtoon] 완결작 페이지 {} 가져오기 실패", page, e);
                break;
            }
        }
        
        return webtoonIds;
    }

    /**
     * URL에서 titleId 추출
     */
    private String extractTitleId(String url) {
        if (url == null || !url.contains("titleId=")) {
            return null;
        }
        
        try {
            String[] parts = url.split("titleId=");
            if (parts.length > 1) {
                String id = parts[1].split("&")[0];
                return id;
            }
        } catch (Exception e) {
            log.debug("titleId 추출 실패: {}", url);
        }
        
        return null;
    }
}


