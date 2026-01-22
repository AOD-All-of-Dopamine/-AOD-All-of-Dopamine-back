package com.example.crawler.ranking.tmdb.fetcher;

import com.example.crawler.ranking.tmdb.constant.TmdbPlatformType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * TMDB API 호출 전담 클래스 (SRP 준수)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbRankingFetcher {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tmdb.api.key:}")
    private String tmdbApiKey;

    @Value("${tmdb.api.base-url:https://api.themoviedb.org/3}")
    private String tmdbBaseUrl;

    private static final String LANGUAGE = "ko-KR";
    private static final int DEFAULT_PAGE = 1;
    private static final int MAX_PAGES_TO_FETCH = 3; // 충분한 데이터 확보를 위해 최대 3페이지까지 가져오기
    // 한국 OTT 플랫폼 필터 (Netflix, Watcha, Disney Plus, Wavve, Tving)
    private static final String WATCH_PROVIDERS = "8|97|337|356|474";
    private static final String WATCH_REGION = "KR";

    /**
     * 통합된 TMDB 랭킹 데이터 조회 메서드 (DRY 원칙)
     */
    public JsonNode fetchPopularContent(TmdbPlatformType platformType) {
        String url = buildUrl(platformType, DEFAULT_PAGE);
        
        try {
            log.debug("TMDB API 호출: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("TMDB {} 랭킹을 가져오는 중 오류 발생: {}", platformType.name(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 다중 페이지에서 TMDB 랭킹 데이터 조회 (필터링 후 충분한 데이터 확보용)
     * @param platformType 플랫폼 타입 (MOVIE/TV)
     * @param maxPages 가져올 최대 페이지 수
     * @return 모든 페이지의 results를 합친 데이터
     */
    public JsonNode fetchMultiplePagesContent(TmdbPlatformType platformType, int maxPages) {
        List<JsonNode> allResults = new ArrayList<>();
        
        for (int page = 1; page <= maxPages; page++) {
            try {
                String url = buildUrl(platformType, page);
                log.debug("TMDB API 호출 (페이지 {}): {}", page, url);
                
                String response = restTemplate.getForObject(url, String.class);
                JsonNode jsonData = objectMapper.readTree(response);
                
                if (jsonData != null && jsonData.has("results")) {
                    JsonNode results = jsonData.get("results");
                    results.forEach(allResults::add);
                    log.debug("페이지 {}: {}개 항목 수집됨", page, results.size());
                }
            } catch (Exception e) {
                log.error("TMDB {} 랭킹 페이지 {} 조회 실패: {}", platformType.name(), page, e.getMessage());
                break; // 오류 발생 시 중단
            }
        }
        
        // 모든 결과를 하나의 JsonNode로 합치기
        try {
            com.fasterxml.jackson.databind.node.ObjectNode combined = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode resultsArray = objectMapper.createArrayNode();
            allResults.forEach(resultsArray::add);
            combined.set("results", resultsArray);
            
            log.info("총 {}개 페이지에서 {}개 항목 수집 완료", maxPages, allResults.size());
            return combined;
        } catch (Exception e) {
            log.error("결과 병합 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 하위 호환성을 위한 메서드 (Deprecated)
     */
    @Deprecated
    public JsonNode fetchPopularMovies() {
        return fetchPopularContent(TmdbPlatformType.MOVIE);
    }

    @Deprecated
    public JsonNode fetchPopularTvShows() {
        return fetchPopularContent(TmdbPlatformType.TV);
    }

    /**
     * 특정 콘텐츠의 Watch Providers 정보 조회
     */
    public JsonNode fetchWatchProviders(TmdbPlatformType platformType, String tmdbId) {
        String endpoint = platformType == TmdbPlatformType.MOVIE ? "movie" : "tv";
        String url = String.format("%s/%s/%s/watch/providers?api_key=%s",
                tmdbBaseUrl,
                endpoint,
                tmdbId,
                tmdbApiKey);
        
        try {
            log.debug("TMDB Watch Providers API 호출: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.warn("TMDB Watch Providers 조회 실패 (ID: {}): {}", tmdbId, e.getMessage());
            return null;
        }
    }

    private String buildUrl(TmdbPlatformType platformType, int page) {
        // with_watch_providers의 | 문자가 URL 인코딩되면 TMDB API가 OR 연산을 인식하지 못하므로
        // 수동으로 URL을 구성하여 인코딩 방지
        return String.format("%s/%s?api_key=%s&language=%s&page=%d&sort_by=popularity.desc&with_watch_providers=%s&watch_region=%s",
                tmdbBaseUrl,
                platformType.getApiPath(),
                tmdbApiKey,
                LANGUAGE,
                page,
                WATCH_PROVIDERS,
                WATCH_REGION);
    }
}


