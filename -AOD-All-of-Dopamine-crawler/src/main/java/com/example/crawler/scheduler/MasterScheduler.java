package com.example.crawler.scheduler;

import com.example.crawler.contents.TMDB.service.TmdbService;
import com.example.crawler.contents.TMDB.service.TmdbSchedulingService;
import com.example.crawler.contents.Webtoon.NaverWebtoon.NaverWebtoonSchedulingService;
import com.example.crawler.contents.Novel.NaverSeriesNovel.NaverSeriesSchedulingService;
import com.example.crawler.game.steam.service.SteamSchedulingService;
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
     * ===== 크롤링 스케줄 =====
     */

    // Steam 게임 크롤링 - 매주 목요일 새벽 3시
    @Scheduled(cron = "0 0 3 * * THU")
    public void scheduleSteamCrawling() {
        log.info("🚀 [Master] Steam 게임 크롤링 스케줄 시작");
        steamSchedulingService.collectSteamGamesWeekly();
    }

    // TMDB 신규 콘텐츠 - 매일 새벽 1시
    @Scheduled(cron = "0 0 1 * * *")
    public void scheduleTmdbNewContent() {
        log.info("🚀 [Master] TMDB 신규 콘텐츠 크롤링 스케줄 시작");
        tmdbSchedulingService.collectNewContentDaily();
    }

    // TMDB 과거 콘텐츠 업데이트 - 매일 새벽 5시
    // TODO: updatePastContentDaily 메서드 구현 필요
    // @Scheduled(cron = "0 0 5 * * *")
    // public void scheduleTmdbPastContent() {
    //     log.info("🚀 [Master] TMDB 과거 콘텐츠 업데이트 스케줄 시작");
    //     tmdbSchedulingService.updatePastContentDaily();
    // }

    // 네이버 웹툰 - 매일 새벽 2시
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduleNaverWebtoon() {
        log.info("🚀 [Master] 네이버 웹툰 크롤링 스케줄 시작");
        naverWebtoonSchedulingService.collectAllWeekdaysDaily();
    }

    // 네이버 웹툰 완결작 - 매주 일요일 새벽 3시
    @Scheduled(cron = "0 0 3 * * SUN")
    public void scheduleNaverWebtoonFinished() {
        log.info("🚀 [Master] 네이버 웹툰 완결작 크롤링 스케줄 시작");
        naverWebtoonSchedulingService.collectFinishedWebtoonsWeekly();
    }

    // 네이버 시리즈 신작 - 매일 새벽 4시
    // TODO: crawlRecentNovelsDaily 메서드 구현 필요
    // @Scheduled(cron = "0 0 4 * * *")
    // public void scheduleNaverSeriesRecent() {
    //     log.info("🚀 [Master] 네이버 시리즈 신작 크롤링 스케줄 시작");
    //     naverSeriesSchedulingService.crawlRecentNovelsDaily();
    // }

    /**
     * ===== Transform 스케줄 =====
     */

    // Transform 배치 - 10분마다
    // TODO: scheduledTransform 메서드 구현 필요
    // @Scheduled(fixedDelay = 600000) // 10분 = 600,000ms
    // public void scheduleTransform() {
    //     log.info("🔄 [Master] Transform 배치 스케줄 시작");
    //     transformSchedulingService.scheduledTransform();
    // }

    /**
     * ===== 모니터링 =====
     */

    // 전체 상태 로깅 - 1시간마다
    @Scheduled(cron = "0 0 * * * *")
    public void logStatus() {
        log.info("📊 [Master] 크롤러 서버 상태: 정상 동작 중");
    }
}


