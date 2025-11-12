package com.example.AOD.ranking.tmdb.service;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.repo.ExternalRankingRepository;
import com.example.AOD.ranking.tmdb.constant.TmdbPlatformType;
import com.example.AOD.ranking.tmdb.fetcher.TmdbRankingFetcher;
import com.example.AOD.ranking.tmdb.mapper.TmdbRankingMapper;
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
    private final ExternalRankingRepository rankingRepository;

    @Transactional
    public void updatePopularMoviesRanking() {
        updateRanking(TmdbPlatformType.MOVIE);
    }

    @Transactional
    public void updatePopularTvShowsRanking() {
        updateRanking(TmdbPlatformType.TV);
    }

    /**
     * 통합된 랭킹 업데이트 로직 (DRY, SRP 준수)
     */
    private void updateRanking(TmdbPlatformType platformType) {
        log.info("TMDB {} 랭킹 업데이트를 시작합니다.", platformType.name());

        // 1. API 호출
        JsonNode jsonData = tmdbRankingFetcher.fetchPopularContent(platformType);
        
        if (jsonData == null || !jsonData.has("results")) {
            log.warn("TMDB {} 랭킹 정보를 가져오지 못했습니다.", platformType.name());
            return;
        }

        // 2. 엔티티 변환
        List<ExternalRanking> rankings = tmdbRankingMapper.mapToRankings(jsonData, platformType);

        if (rankings.isEmpty()) {
            log.warn("변환된 TMDB {} 랭킹 데이터가 없습니다.", platformType.name());
            return;
        }

        // 3. 기존 데이터 삭제 후 저장 (일관성 보장)
        deleteExistingRankings(platformType.getPlatformName());
        rankingRepository.saveAll(rankings);

        log.info("TMDB {} 랭킹 업데이트 완료. 총 {}개", platformType.name(), rankings.size());
    }

    /**
     * 기존 랭킹 데이터 삭제 (트랜잭션 내에서 안전하게 처리)
     */
    private void deleteExistingRankings(String platform) {
        List<ExternalRanking> existingRankings = rankingRepository.findByPlatform(platform);
        
        if (!existingRankings.isEmpty()) {
            rankingRepository.deleteAllInBatch(existingRankings);
            log.debug("기존 {} 랭킹 {}개를 삭제했습니다.", platform, existingRankings.size());
        }
    }
}
