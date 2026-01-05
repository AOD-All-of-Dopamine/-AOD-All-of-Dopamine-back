package com.example.crawler.ranking.service;

import com.example.crawler.ranking.Webtoon.NaverWebtoon.service.NaverWebtoonRankingService;
import com.example.crawler.ranking.Webnovel.NaverSeries.service.NaverSeriesRankingService;
import com.example.crawler.ranking.steam.service.SteamRankingService;
import com.example.crawler.ranking.tmdb.service.TmdbRankingService;
import com.example.shared.entity.ExternalRanking;
import com.example.shared.repository.ExternalRankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 통합 랭킹 크롤링 서비스 (Crawler 서버)
 * - 모든 플랫폼의 랭킹을 한번에 크롤링
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingCrawlerService {

    private final NaverWebtoonRankingService naverWebtoonRankingService;
    private final NaverSeriesRankingService naverSeriesRankingService;
    private final SteamRankingService steamRankingService;
    private final TmdbRankingService tmdbRankingService;
    private final ExternalRankingRepository rankingRepository;

    /**
     * 모든 플랫폼의 랭킹을 크롤링하여 업데이트하고 결과를 반환합니다.
     * - 네이버 웹툰 (오늘 요일 기준)
     * - 네이버 시리즈 (웹소설 일간)
     * - Steam (최고 판매)
     * - TMDB (인기 영화 & TV 쇼)
     */
    @Transactional
    public List<ExternalRanking> crawlAndGetAllRankings() {
        log.info("전체 플랫폼 랭킹 크롤링을 시작합니다.");
        
        try {
            // 1. 네이버 웹툰
            log.info("1/5 - 네이버 웹툰 랭킹 크롤링 중...");
            naverWebtoonRankingService.updateTodayWebtoonRanking();
            
            // 2. 네이버 시리즈
            log.info("2/5 - 네이버 시리즈 랭킹 크롤링 중...");
            naverSeriesRankingService.updateDailyRanking();
            
            // 3. Steam
            log.info("3/5 - Steam 랭킹 크롤링 중...");
            steamRankingService.updateTopSellersRanking();
            
            // 4. TMDB 영화
            log.info("4/5 - TMDB 영화 랭킹 크롤링 중...");
            tmdbRankingService.updatePopularMoviesRanking();
            
            // 5. TMDB TV 쇼
            log.info("5/5 - TMDB TV 쇼 랭킹 크롤링 중...");
            tmdbRankingService.updatePopularTvShowsRanking();
            
            log.info("전체 플랫폼 랭킹 크롤링이 완료되었습니다.");
            
        } catch (Exception e) {
            log.error("전체 랭킹 크롤링 중 오류 발생", e);
            throw e;
        }
        
        // 크롤링 완료 후 전체 랭킹 반환
        return rankingRepository.findAll();
    }

    /**
     * 네이버 웹툰 랭킹만 크롤링
     */
    @Transactional
    public void crawlNaverWebtoonRanking() {
        log.info("네이버 웹툰 랭킹 크롤링 시작");
        naverWebtoonRankingService.updateTodayWebtoonRanking();
        log.info("네이버 웹툰 랭킹 크롤링 완료");
    }

    /**
     * 네이버 시리즈 랭킹만 크롤링
     */
    @Transactional
    public void crawlNaverSeriesRanking() {
        log.info("네이버 시리즈 랭킹 크롤링 시작");
        naverSeriesRankingService.updateDailyRanking();
        log.info("네이버 시리즈 랭킹 크롤링 완료");
    }

    /**
     * Steam 랭킹만 크롤링
     */
    @Transactional
    public void crawlSteamRanking() {
        log.info("Steam 랭킹 크롤링 시작");
        steamRankingService.updateTopSellersRanking();
        log.info("Steam 랭킹 크롤링 완료");
    }

    /**
     * TMDB 랭킹만 크롤링 (영화 + TV)
     */
    @Transactional
    public void crawlTmdbRanking() {
        log.info("TMDB 랭킹 크롤링 시작");
        tmdbRankingService.updatePopularMoviesRanking();
        tmdbRankingService.updatePopularTvShowsRanking();
        log.info("TMDB 랭킹 크롤링 완료");
    }
}
