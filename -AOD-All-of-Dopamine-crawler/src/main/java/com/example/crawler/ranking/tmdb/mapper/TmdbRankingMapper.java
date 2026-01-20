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

    /**
     * TMDB JSON 데이터를 ExternalRanking 엔티티 리스트로 변환
     * @param jsonNode TMDB API 응답 데이터
     * @param platformType 플랫폼 타입 (MOVIE/TV)
     * @param minVoteCount 최소 투표수 필터링 기준 (이 값 미만인 콘텐츠는 제외)
     * @return 필터링된 ExternalRanking 리스트
     */
    public List<ExternalRanking> mapToRankings(JsonNode jsonNode, TmdbPlatformType platformType, int minVoteCount) {
        if (jsonNode == null || !jsonNode.has("results")) {
            log.warn("유효하지 않은 TMDB 응답 데이터입니다.");
            return new ArrayList<>();
        }

        List<ExternalRanking> rankings = new ArrayList<>();
        int rank = 1;
        int filteredCount = 0;

        for (JsonNode item : jsonNode.get("results")) {
            try {
                // 최소 투표수 필터링
                int voteCount = item.has("vote_count") ? item.get("vote_count").asInt() : 0;
                if (voteCount < minVoteCount) {
                    String title = item.has(platformType.getTitleField()) 
                            ? item.get(platformType.getTitleField()).asText() 
                            : "Unknown";
                    log.debug("최소 투표수 미달로 제외: {} (vote_count: {} < {})", title, voteCount, minVoteCount);
                    filteredCount++;
                    continue;
                }

                ExternalRanking ranking = createRanking(item, rank++, platformType);
                rankings.add(ranking);
            } catch (Exception e) {
                log.warn("TMDB 랭킹 항목 변환 중 오류 발생 (건너뜀): {}", e.getMessage());
            }
        }

        if (filteredCount > 0) {
            log.info("최소 투표수({}) 미달로 {}개 콘텐츠 제외됨", minVoteCount, filteredCount);
        }

        return rankings;
    }

    /**
     * TMDB JSON 데이터를 ExternalRanking 엔티티 리스트로 변환 (개수 제한 포함)
     * 필터링 후 상위 N개만 선택하여 정확한 개수를 보장
     * @param jsonNode TMDB API 응답 데이터
     * @param platformType 플랫폼 타입 (MOVIE/TV)
     * @param minVoteCount 최소 투표수 필터링 기준
     * @param maxCount 최대 반환 개수
     * @return 필터링 및 개수 제한된 ExternalRanking 리스트
     */
    public List<ExternalRanking> mapToRankingsWithLimit(JsonNode jsonNode, TmdbPlatformType platformType, 
                                                          int minVoteCount, int maxCount) {
        if (jsonNode == null || !jsonNode.has("results")) {
            log.warn("유효하지 않은 TMDB 응답 데이터입니다.");
            return new ArrayList<>();
        }

        List<ExternalRanking> rankings = new ArrayList<>();
        int rank = 1;
        int filteredCount = 0;
        int totalProcessed = 0;

        for (JsonNode item : jsonNode.get("results")) {
            totalProcessed++;
            
            // 이미 필요한 개수만큼 수집했으면 종료
            if (rankings.size() >= maxCount) {
                break;
            }

            try {
                // 최소 투표수 필터링
                int voteCount = item.has("vote_count") ? item.get("vote_count").asInt() : 0;
                if (voteCount < minVoteCount) {
                    String title = item.has(platformType.getTitleField()) 
                            ? item.get(platformType.getTitleField()).asText() 
                            : "Unknown";
                    log.debug("최소 투표수 미달로 제외: {} (vote_count: {} < {})", title, voteCount, minVoteCount);
                    filteredCount++;
                    continue;
                }

                ExternalRanking ranking = createRanking(item, rank++, platformType);
                rankings.add(ranking);
            } catch (Exception e) {
                log.warn("TMDB 랭킹 항목 변환 중 오류 발생 (건너뜀): {}", e.getMessage());
            }
        }

        log.info("TMDB {} 랭킹 변환 완료: 처리 {}개, 필터링 제외 {}개, 최종 선택 {}개 (목표: {}개)", 
                platformType.name(), totalProcessed, filteredCount, rankings.size(), maxCount);

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


