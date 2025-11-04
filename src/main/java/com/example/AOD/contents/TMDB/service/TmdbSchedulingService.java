package com.example.AOD.contents.TMDB.service;

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

    // 과거 데이터 업데이트를 위한 연도 추적 변수 (현재 연도로 시작)
    private int yearToUpdate = Year.now().getValue();
    private static final int OLDEST_YEAR = 1980; // 업데이트할 가장 오래된 연도

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
     * 과거 콘텐츠 최신화를 위해 매주 일요일 새벽 5시에 실행됩니다.
     * 지정된 연도의 모든 영화 및 TV쇼 데이터를 수집하고, 다음 실행을 위해 연도를 1씩 감소시킵니다.
     * @Scheduled 메서드는 즉시 반환하고, 실제 작업은 비동기로 실행됩니다.
     */
    @Scheduled(cron = "0 0 5 * * SUN") // 매주 일요일 새벽 5시
    public void updatePastContentWeekly() {
        if (yearToUpdate < OLDEST_YEAR) {
            log.info("모든 과거 콘텐츠 순환 업데이트가 1회 완료되었습니다. 다음 주부터 다시 현재 연도부터 시작합니다.");
            yearToUpdate = Year.now().getValue(); // 가장 오래된 연도까지 갔으면 다시 현재 연도로 리셋
        }

        int currentYear = yearToUpdate;
        log.info("🚀 [정기 스케줄] 과거 콘텐츠 최신화 스케줄 트리거됨. (대상 연도: {})", currentYear);
        String language = "ko-KR";

        // 비동기로 실행 - 스케줄러 스레드는 즉시 반환
        tmdbService.updatePastContentAsync(currentYear, language);
        
        // 다음 주에 업데이트할 연도 설정
        yearToUpdate--;
    }
}