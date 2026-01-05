package com.example.crawler.ranking.tmdb.mapper;

import com.example.shared.entity.ExternalRanking;
import com.example.crawler.ranking.tmdb.constant.TmdbPlatformType;
import com.example.crawler.ranking.tmdb.fetcher.TmdbRankingFetcher;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * TMDB JSON 데이터를 ExternalRanking 엔티티로 변환 (SRP 준수)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbRankingMapper {

    private final TmdbRankingFetcher tmdbRankingFetcher;

    public List<ExternalRanking> mapToRankings(JsonNode jsonNode, TmdbPlatformType platformType) {
        if (jsonNode == null || !jsonNode.has("results")) {
            log.warn("유효하지 않은 TMDB 응답 데이터입니다.");
            return new ArrayList<>();
        }

        List<ExternalRanking> rankings = new ArrayList<>();
        int rank = 1;

        for (JsonNode item : jsonNode.get("results")) {
            try {
                ExternalRanking ranking = createRanking(item, rank++, platformType);
                rankings.add(ranking);
            } catch (Exception e) {
                log.warn("TMDB 랭킹 항목 변환 중 오류 발생 (건너뜀): {}", e.getMessage());
            }
        }

        return rankings;
    }

    private ExternalRanking createRanking(JsonNode item, int rank, TmdbPlatformType platformType) {
        // 필수 필드 검증
        if (!item.has("id") || !item.has(platformType.getTitleField())) {
            throw new IllegalArgumentException("필수 필드가 누락되었습니다: id 또는 " + platformType.getTitleField());
        }

        ExternalRanking ranking = new ExternalRanking();
        String tmdbId = String.valueOf(item.get("id").asLong());
        ranking.setPlatformSpecificId(tmdbId);
        ranking.setTitle(item.get(platformType.getTitleField()).asText());
        ranking.setRanking(rank);
        ranking.setPlatform(platformType.getPlatformName());
        
        // 썸네일 URL 추출 (poster_path)
        if (item.has("poster_path") && !item.get("poster_path").isNull()) {
            String posterPath = item.get("poster_path").asText();
            String thumbnailUrl = "https://image.tmdb.org/t/p/w500" + posterPath;
            ranking.setThumbnailUrl(thumbnailUrl);
        }

        // Watch Providers 추출 (한국 지역 flatrate)
        List<String> watchProviders = extractWatchProviders(platformType, tmdbId);
        if (!watchProviders.isEmpty()) {
            ranking.setWatchProviders(watchProviders);
        }

        return ranking;
    }

    /**
     * TMDB Watch Providers API에서 한국 지역 OTT 플랫폼 정보 추출
     */
    private List<String> extractWatchProviders(TmdbPlatformType platformType, String tmdbId) {
        try {
            JsonNode watchProvidersData = tmdbRankingFetcher.fetchWatchProviders(platformType, tmdbId);
            
            if (watchProvidersData == null || !watchProvidersData.has("results")) {
                return new ArrayList<>();
            }

            JsonNode results = watchProvidersData.get("results");
            if (!results.has("KR")) {
                return new ArrayList<>();
            }

            JsonNode krData = results.get("KR");
            if (!krData.has("flatrate")) {
                return new ArrayList<>();
            }

            JsonNode flatrate = krData.get("flatrate");
            return StreamSupport.stream(flatrate.spliterator(), false)
                    .filter(provider -> provider.has("provider_name"))
                    .map(provider -> provider.get("provider_name").asText())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.warn("Watch Providers 추출 실패 (ID: {}): {}", tmdbId, e.getMessage());
            return new ArrayList<>();
        }
    }
}


