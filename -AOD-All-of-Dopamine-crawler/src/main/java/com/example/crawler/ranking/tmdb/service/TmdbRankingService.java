package com.example.crawler.ranking.tmdb.service;

import com.example.crawler.ranking.common.RankingUpsertHelper;
import com.example.shared.entity.ExternalRanking;
import com.example.crawler.ranking.tmdb.constant.TmdbPlatformType;
import com.example.crawler.ranking.tmdb.fetcher.TmdbRankingFetcher;
import com.example.crawler.ranking.tmdb.mapper.TmdbRankingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TMDB 랭킹 서비스 (리팩토링됨 - SOLID 원칙 준수)
 * - SRP: 책임 분리 (Fetcher, Mapper, Service)
 * - OCP: Enum 활용으로 확장 용이
 * - DRY: 중복 코드 제거
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbRankingService {

    private final TmdbRankingFetcher tmdbRankingFetcher;
    private final TmdbRankingMapper tmdbRankingMapper;
    private final RankingUpsertHelper rankingUpsertHelper;

    private static final int MAX_RANKING_SIZE = 20; // 최종적으로 저장할 랭킹 개수
    private static final int MAX_PAGES_TO_FETCH = 3; // 충분한 데이터 확보를 위해 최대 3페이지까지

    @Transactional(timeout = 60) // Watch Providers 조회로 시간이 걸리므로 60초로 설정
    public void updatePopularMoviesRanking(int minVoteCount) {
        updateRanking(TmdbPlatformType.MOVIE, minVoteCount);
    }

    @Transactional(timeout = 60) // Watch Providers 조회로 시간이 걸리므로 60초로 설정
    public void updatePopularTvShowsRanking(int minVoteCount) {
        updateRanking(TmdbPlatformType.TV, minVoteCount);
    }

    /**
     * 통합된 랭킹 업데이트 로직 (DRY, SRP 준수)
     * 다중 페이지를 가져와서 필터링 후에도 정확히 20개를 확보
     * @param platformType 플랫폼 타입 (MOVIE/TV)
     * @param minVoteCount 최소 투표수 필터링 기준
     */
    private void updateRanking(TmdbPlatformType platformType, int minVoteCount) {
        log.info("TMDB {} 랭킹 업데이트를 시작합니다. (최소 투표수: {}, 목표: {}개)", 
                platformType.name(), minVoteCount, MAX_RANKING_SIZE);

        // 1. 다중 페이지 API 호출 (충분한 데이터 확보)
        JsonNode jsonData = tmdbRankingFetcher.fetchMultiplePagesContent(platformType, MAX_PAGES_TO_FETCH);
        
        if (jsonData == null || !jsonData.has("results")) {
            log.warn("TMDB {} 랭킹 정보를 가져오지 못했습니다.", platformType.name());
            return;
        }

        // 2. 엔티티 변환 (최소 투표수 필터링 적용 및 상위 20개 선택)
        List<ExternalRanking> rankings = tmdbRankingMapper.mapToRankingsWithLimit(
                jsonData, platformType, minVoteCount, MAX_RANKING_SIZE);

        if (rankings.isEmpty()) {
            log.warn("변환된 TMDB {} 랭킹 데이터가 없습니다.", platformType.name());
            return;
        }

        // 3. 기존 데이터와 병합하여 저장 (ID 유지) - Helper 사용
        rankingUpsertHelper.upsertRankings(rankings, platformType.getPlatformName());

        log.info("TMDB {} 랭킹 업데이트 완료. 총 {}개 (목표: {}개)", 
                platformType.name(), rankings.size(), MAX_RANKING_SIZE);
    }

}


