package com.example.crawler.ranking.Webtoon.NaverWebtoon.fetcher;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 네이버 웹툰 랭킹 페이지 Fetcher
 * - 현재 요일의 웹툰 목록을 가져옴
 * - 모바일 페이지 사용 (정적 HTML, Jsoup으로 충분)
 */
@Slf4j
@Component
public class NaverWebtoonRankingFetcher {

    private static final String BASE_WEEKDAY_URL = "https://m.comic.naver.com/webtoon/weekday?week=";
    private static final String[] WEEKDAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    /**
     * 오늘 요일의 웹툰 목록 페이지 가져오기
     */
    public Document fetchTodayWebtoons() {
        String today = getTodayWeekday();
        String url = BASE_WEEKDAY_URL + today;
        
        log.info("오늘({}) 요일 네이버 웹툰 랭킹 페이지 가져오기: {}", today, url);
        
        try {
            return Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "ko-KR,ko;q=0.9")
                    .timeout(15000)
                    .get();
        } catch (IOException e) {
            log.error("네이버 웹툰 랭킹 페이지를 가져오는 중 오류 발생: url={}, error={}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 현재 요일을 영문 약자로 반환
     * mon, tue, wed, thu, fri, sat, sun
     */
    private String getTodayWeekday() {
        DayOfWeek dayOfWeek = LocalDate.now().getDayOfWeek();
        return WEEKDAYS[dayOfWeek.getValue() - 1]; // 월요일=1, 일요일=7
    }

    /**
     * 현재 요일 문자열을 외부에서 접근 가능하도록 public 메서드 제공
     */
    public String getTodayWeekdayString() {
        return getTodayWeekday();
    }
}


