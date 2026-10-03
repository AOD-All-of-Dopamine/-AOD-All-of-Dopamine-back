package com.example.crawler.ranking.tmdb;

import com.example.crawler.ranking.RankingUpsertHelper;
import com.example.shared.entity.ExternalRanking;
import com.example.crawler.ranking.tmdb.TmdbPlatformType;
import com.example.crawler.ranking.tmdb.TmdbRankingFetcher;
import com.example.crawler.ranking.tmdb.TmdbRankingMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    private static final int MAX_RANKING_SIZE = 100; // 최종적으로 저장할 랭킹 개수
    private static final int MAX_PAGES_TO_FETCH = 7; // 충분한 데이터 확보를 위해 최대 8페이지까지

    public void updatePopularMoviesRanking(int minVoteCount) {
        updateRanking(TmdbPlatformType.MOVIE, minVoteCount);
    }

    public void updatePopularTvShowsRanking(int minVoteCount) {
        updateRanking(TmdbPlatformType.TV, minVoteCount);
    }

    /**
     * 통합된 랭킹 업데이트 로직 (DRY, SRP 준수)
     * 다중 페이지를 가져와서 필터링 후에도 정확히 20개를 확보
     * @param platformType 플랫폼 타입 (MOVIE/TV)
     * @param minVoteCount 최소 투표수 필터링 기준
     */
    void attachLogos(List<ExternalRanking> rankings, TmdbPlatformType platformType) {
        int candidates = 0, logos = 0;
        for (ExternalRanking row : rankings) {
            if (!com.example.shared.featured.FeaturedGates.isCandidate(row)) {
                row.setLogoChecked(true);
                continue;
            }
            candidates++;
            try {
                java.util.Optional<String[]> logo = tmdbRankingFetcher.fetchLogo(platformType, row.getPlatformSpecificId());
                logo.ifPresent(l -> {
                    row.setLogoUrl(l[0]);
                    row.setLogoLang(l[1]);
                });
                row.setLogoChecked(true);
                if (logo.isPresent()) logos++;
            } catch (Exception e) {
                log.warn("TMDB 로고 조회 실패 — 옛 값을 둔다 id={}: {}", row.getPlatformSpecificId(), e.getMessage());
            }
        }
        log.info("TMDB {} 히어로 — 배경 {}/{} · 후보 {} · 로고 {}", platformType.name(),
                rankings.stream().filter(r -> r.getBackdropUrl() != null).count(), rankings.size(), candidates, logos);
    }

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

        // 2-1. 히어로 로고 — 오늘의 작품 문턱을 넘는 30위 이내만(그 밖은 없음으로 확인)
        attachLogos(rankings, platformType);

        // 3. 기존 데이터와 병합하여 저장 (ID 유지) - Helper 사용
        rankingUpsertHelper.upsertRankings(rankings, platformType.getPlatformName());

        log.info("TMDB {} 랭킹 업데이트 완료. 총 {}개 (목표: {}개)", 
                platformType.name(), rankings.size(), MAX_RANKING_SIZE);
    }

}


