package com.example.crawler.game.steam.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Steam 정기 크롤링 스케줄러
 * - crawlerTaskExecutor 스레드풀 사용
 * - 비동기 실행으로 스케줄러 스레드 블로킹 방지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamSchedulingService {

    private final SteamCrawlService steamCrawlService;

    /**
     * 매주 목요일 새벽 3시에 Steam 신규 게임 수집
     * - 전체 게임 목록을 1000개씩 자동 분할 수집
     * - 비동기 실행으로 대량 데이터 처리
     */
    @Scheduled(cron = "0 0 3 * * THU") // 매주 목요일 새벽 3시
    public void collectSteamGamesWeekly() {
        log.info("🚀 [정기 스케줄] Steam 전체 게임 데이터 수집 시작");
        
        try {
            // 비동기로 실행 - crawlerTaskExecutor 사용
            // 내부적으로 1000개씩 자동 분할 처리
            steamCrawlService.collectAllGamesInBatches();
            
            log.info("✅ [정기 스케줄] Steam 게임 수집 작업 트리거 완료 (비동기 실행 중)");
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] Steam 게임 수집 트리거 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 매월 15일 새벽 4시에 기존 게임 정보 업데이트
     * - 가격, 리뷰, 메타크리틱 점수 등 업데이트
     * - 대규모 작업이므로 월 1회 실행
     */
    @Scheduled(cron = "0 0 4 15 * *") // 매월 15일 새벽 4시
    public void updateExistingGamesMonthly() {
        log.info("🚀 [정기 스케줄] Steam 기존 게임 정보 업데이트 시작");
        
        try {
            // 비동기로 실행 - 전체 게임 재수집으로 업데이트
            steamCrawlService.collectAllGamesInBatches();
            
            log.info("✅ [정기 스케줄] Steam 게임 업데이트 작업 트리거 완료 (비동기 실행 중)");
        } catch (Exception e) {
            log.error("❌ [정기 스케줄] Steam 게임 업데이트 트리거 실패: {}", e.getMessage(), e);
        }
    }
}


