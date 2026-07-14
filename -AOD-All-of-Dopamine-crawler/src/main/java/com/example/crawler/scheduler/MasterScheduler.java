package com.example.crawler.scheduler;

import com.example.crawler.contents.tmdb.TmdbSchedulingService;
import com.example.crawler.contents.webtoon.naverwebtoon.NaverWebtoonSchedulingService;
import com.example.crawler.contents.novel.naverseries.NaverSeriesSchedulingService;
import com.example.crawler.contents.game.steam.SteamSchedulingService;
import com.example.crawler.ingest.TransformSchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 크롤러 서버 통합 스케줄러
 * - 모든 크롤링 및 Transform 작업을 관리
 * - 각 도메인별 스케줄링 서비스를 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterScheduler {

    private final SteamSchedulingService steamSchedulingService;
    private final TmdbSchedulingService tmdbSchedulingService;
    private final NaverWebtoonSchedulingService naverWebtoonSchedulingService;
    private final NaverSeriesSchedulingService naverSeriesSchedulingService;
    private final TransformSchedulingService transformSchedulingService;

    /**
     * ===== 크롤링 스케줄 (Job Queue 기반) =====
     * 
     * 각 스케줄은 크롤링 대상 목록을 Job Queue에 등록만 합니다.
     * 실제 크롤링은 Consumer가 5초마다 균등하게 처리합니다.
     */

    // Steam 게임 크롤링 - 매주 목요일 새벽 3시
    @Scheduled(cron = "0 0 3 * * THU")
    public void scheduleSteamCrawling() {
        log.info("🚀 [Master] Steam 게임 목록 Job Queue 등록 시작");
        steamSchedulingService.collectSteamGamesWeekly();
    }

    // TMDB 신규 콘텐츠 - 매일 새벽 1시
    @Scheduled(cron = "0 0 1 * * *")
    public void scheduleTmdbNewContent() {
        log.info("🚀 [Master] TMDB 신규 콘텐츠 Job Queue 등록 시작");
        tmdbSchedulingService.collectNewContentDaily();
    }

    // 네이버 웹툰 - 매일 새벽 2시
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduleNaverWebtoon() {
        log.info("🚀 [Master] 네이버 웹툰 Job Queue 등록 시작");
        naverWebtoonSchedulingService.collectAllWeekdaysDaily();
    }

    // 네이버 웹툰 완결작 - 매주 일요일 새벽 3시
    @Scheduled(cron = "0 0 3 * * SUN")
    public void scheduleNaverWebtoonFinished() {
        log.info("🚀 [Master] 네이버 웹툰 완결작 Job Queue 등록 시작");
        naverWebtoonSchedulingService.collectFinishedWebtoonsWeekly();
    }

    // 네이버 시리즈 웹소설 신작 - 매일 새벽 1시 30분
    @Scheduled(cron = "0 30 1 * * *")
    public void scheduleNaverSeriesNovel() {
        log.info("🚀 [Master] 네이버 시리즈 웹소설 신작 Job Queue 등록 시작");
        naverSeriesSchedulingService.collectRecentNovelsDaily();
    }

    // 네이버 시리즈 웹소설 완결작 - 매주 토요일 새벽 3시 30분
    @Scheduled(cron = "0 30 3 * * SAT")
    public void scheduleNaverSeriesNovelCompleted() {
        log.info("🚀 [Master] 네이버 시리즈 웹소설 완결작 Job Queue 등록 시작");
        naverSeriesSchedulingService.collectCompletedNovelsWeekly();
    }

    /**
     * ===== 랭킹 크롤링 스케줄 =====
     * 
     * RankingScheduler에서 자체 스케줄링 처리
     * - 매일 새벽 4시: 모든 플랫폼 랭킹 크롤링
     *   (네이버 웹툰, 네이버 시리즈, Steam, TMDB 영화/TV)
     */

    /**
     * ===== Transform 스케줄 =====
     * 
     * TransformSchedulingService에서 자체 스케줄링 처리
     * - 매일 새벽 6시: 100개씩 배치 처리
     * - 매주 일요일 7시: 200개씩 대규모 배치 처리
     */

    /**
     * ===== 모니터링 =====
     */

    // 전체 상태 로깅 - 1시간마다
    @Scheduled(cron = "0 0 * * * *")
    public void logStatus() {
        log.info("📊 [Master] 크롤러 서버 상태: 정상 동작 중");
    }
}


