package com.example.AOD.ranking.service;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.repo.ExternalRankingRepository;
import com.example.AOD.ranking.Webtoon.NaverWebtoon.service.NaverWebtoonRankingService;
import com.example.AOD.ranking.Webnovel.NaverSeries.service.NaverSeriesRankingService;
import com.example.AOD.ranking.steam.service.SteamRankingService;
import com.example.AOD.ranking.tmdb.service.TmdbRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final ExternalRankingRepository rankingRepository;
    private final NaverWebtoonRankingService naverWebtoonRankingService;
    private final NaverSeriesRankingService naverSeriesRankingService;
    private final SteamRankingService steamRankingService;
    private final TmdbRankingService tmdbRankingService;

    @Transactional
    public void saveRankings(List<ExternalRanking> rankings) {
        // 기존 랭킹 정보를 지우고 새로 저장하거나, 플랫폼별로 업데이트하는 로직 추가 가능
        rankingRepository.saveAll(rankings);
    }

    @Transactional(readOnly = true)
    public List<ExternalRanking> getRankingsByPlatform(String platform) {
        return rankingRepository.findByPlatformWithContent(platform);
    }

    @Transactional(readOnly = true)
    public List<ExternalRanking> getAllRankings() {
        return rankingRepository.findAllWithContent();
    }

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
        }
        
        // 크롤링 완료 후 전체 랭킹 반환
        return rankingRepository.findAll();
    }
}
