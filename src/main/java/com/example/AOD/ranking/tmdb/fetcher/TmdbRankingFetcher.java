package com.example.AOD.ranking.tmdb.fetcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbRankingFetcher {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tmdb.api.key:}")
    private String tmdbApiKey;

    private static final String POPULAR_MOVIES_URL = "https://api.themoviedb.org/3/movie/popular?api_key={apiKey}&language=ko-KR&page=1";
    private static final String POPULAR_TV_URL = "https://api.themoviedb.org/3/tv/popular?api_key={apiKey}&language=ko-KR&page=1";

    public JsonNode fetchPopularMovies() {
        try {
            String response = restTemplate.getForObject(POPULAR_MOVIES_URL, String.class, tmdbApiKey);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("TMDB 인기 영화 랭킹을 가져오는 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }

    public JsonNode fetchPopularTvShows() {
        try {
            String response = restTemplate.getForObject(POPULAR_TV_URL, String.class, tmdbApiKey);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            log.error("TMDB 인기 TV 쇼 랭킹을 가져오는 중 오류 발생: {}", e.getMessage());
            return null;
        }
    }
}
