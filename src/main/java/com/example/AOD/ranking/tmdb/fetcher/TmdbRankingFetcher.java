package com.example.AOD.ranking.tmdb.fetcher;

import com.example.AOD.ranking.tmdb.constant.TmdbPlatformType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

    /**
     * 통합된 TMDB 랭킹 데이터 조회 메서드 (DRY 원칙)
     */
    public JsonNode fetchPopularContent(TmdbPlatformType platformType) {
        String url = buildUrl(platformType);
        
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

    private String buildUrl(TmdbPlatformType platformType) {
        return String.format("%s/%s?api_key=%s&language=%s&page=%d",
                tmdbBaseUrl,
                platformType.getApiPath(),
                tmdbApiKey,
                LANGUAGE,
                DEFAULT_PAGE);
    }
}
