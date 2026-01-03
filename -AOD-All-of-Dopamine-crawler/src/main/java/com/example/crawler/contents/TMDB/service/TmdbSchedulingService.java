package com.example.crawler.contents.TMDB.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbSchedulingService {

    private final TmdbService tmdbService;

    private static final int OLDEST_YEAR = 1980; // 전체 크롤링 시 가장 오래된 연도

    /**
     * [개선] 신규 콘텐츠 수집을 위해 매일 새벽 4시에 실행됩니다.
     * 최근 7일간의 영화 및 TV쇼 데이터를 수집합니다.
     * @Scheduled 메서드는 즉시 반환하고, 실제 작업은 비동기로 실행됩니다.
     */
    @Scheduled(cron = "0 0 4 * * *") // 매일 새벽 4시
    public void collectNewContentDaily() {
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysAgo = today.minusDays(7);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String startDate = sevenDaysAgo.format(formatter);
        String endDate = today.format(formatter);
        String language = "ko-KR";

        log.info("🚀 [정기 스케줄] 신규 콘텐츠 수집 스케줄 트리거됨. (기간: {} ~ {})", startDate, endDate);

        // 비동기로 실행 - 스케줄러 스레드는 즉시 반환
        tmdbService.collectNewContentAsync(startDate, endDate, language, 10);
    }

    /**
     * 전체 과거 콘텐츠 크롤링을 위해 매주 일요일 새벽 5시에 실행됩니다.
     * OLDEST_YEAR(1980년)부터 현재 연도까지의 모든 영화 및 TV쇼 데이터를 수집합니다.
     * @Scheduled 메서드는 즉시 반환하고, 실제 작업은 비동기로 실행됩니다.
     */
    @Scheduled(cron = "0 0 5 * * SUN") // 매주 일요일 새벽 5시
    public void updatePastContentWeekly() {
        int currentYear = Year.now().getValue();
        log.info("🚀 [정기 스케줄] 전체 과거 콘텐츠 크롤링 스케줄 트리거됨. (기간: {}년 ~ {}년)", OLDEST_YEAR, currentYear);
        String language = "ko-KR";

        // 비동기로 실행 - 스케줄러 스레드는 즉시 반환
        // OLDEST_YEAR부터 현재 연도까지 전체 데이터 크롤링
        tmdbService.updatePastContentAsync(OLDEST_YEAR, currentYear, language);
    }
}

