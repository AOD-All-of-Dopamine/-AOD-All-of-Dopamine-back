package com.example.crawler.ranking.Webnovel.NaverSeries.fetcher;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 네이버 시리즈(웹소설) 랭킹 페이지 Fetcher
 * - TOP 100 페이지에서 일간 랭킹 가져오기
 * - Jsoup 사용 (정적 HTML)
 */
@Slf4j
@Component
public class NaverSeriesRankingFetcher {

    private static final String TOP_100_URL = "https://series.naver.com/novel/top100List.series?rankingTypeCode=DAILY&categoryCode=ALL&page=1";

    /**
     * 일간 TOP 100 페이지 가져오기 (첫 페이지만)
     */
    public Document fetchDailyTop100() {
        log.info("네이버 시리즈 일간 TOP 100 페이지 가져오기: {}", TOP_100_URL);
        
        try {
            return Jsoup.connect(TOP_100_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .referrer("https://series.naver.com/")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout(15000)
                    .get();
        } catch (IOException e) {
            log.error("네이버 시리즈 랭킹 페이지를 가져오는 중 오류 발생: url={}, error={}", TOP_100_URL, e.getMessage());
            return null;
        }
    }

    /**
     * 상세 페이지 가져오기 (기존 크롤러 로직 재사용)
     */
    public Document fetchDetailPage(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .referrer("https://series.naver.com/")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .timeout(15000)
                    .get();
        } catch (IOException e) {
            log.error("네이버 시리즈 상세 페이지를 가져오는 중 오류 발생: url={}, error={}", url, e.getMessage());
            return null;
        }
    }
}


