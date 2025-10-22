package com.example.AOD.contents.TMDB.fetcher;

import com.example.AOD.contents.TMDB.dto.TmdbDiscoveryResult;
import com.example.AOD.contents.TMDB.dto.TmdbTvDiscoveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@Slf4j
public class TmdbApiFetcher {

    @Value("${tmdb.api.key}")
    private String apiKey;
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 특정 기간과 페이지에 해당하는 영화 목록을 TMDB에서 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/discover-movie
     *
     * @param language  언어 코드
     * @param page      페이지 번호
     * @param startDate 시작일 (YYYY-MM-DD)
     * @param endDate   종료일 (YYYY-MM-DD)
     * @return TmdbDiscoveryResult 객체
     */
    public TmdbDiscoveryResult discoverMovies(String language, int page, String startDate, String endDate) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/movie")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("page", page)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("with_watch_providers", "8|97|337|356") // 예: Netflix, Watcha, Disney Plus, Wavve
                .queryParam("watch_region", "KR")
                .queryParam("primary_release_date.gte", startDate)
                .queryParam("primary_release_date.lte", endDate);

        String url = builder.toUriString();

        try {
            return restTemplate.getForObject(url, TmdbDiscoveryResult.class);
        } catch (Exception e) {
            log.error("Error fetching movies from TMDB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 특정 기간과 페이지에 해당하는 TV쇼 목록을 TMDB에서 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/discover-tv
     *
     * @param language  언어 코드
     * @param page      페이지 번호
     * @param startDate 시작일 (YYYY-MM-DD)
     * @param endDate   종료일 (YYYY-MM-DD)
     * @return TmdbTvDiscoveryResult 객체
     */
    public TmdbTvDiscoveryResult discoverTvShows(String language, int page, String startDate, String endDate) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/tv")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("page", page)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("with_watch_providers", "8|97|337|356")
                .queryParam("watch_region", "KR")
                .queryParam("air_date.gte", startDate)
                .queryParam("air_date.lte", endDate);

        String url = builder.toUriString();

        try {
            return restTemplate.getForObject(url, TmdbTvDiscoveryResult.class);
        } catch (Exception e) {
            log.error("Error fetching TV shows from TMDB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 특정 ID의 영화 상세 정보를 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/movie-details
     *
     * @param movieId  TMDB 영화 ID
     * @param language 언어 코드
     * @return 영화 상세 정보가 담긴 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMovieDetails(int movieId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/movie/" + movieId)
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("append_to_response", "credits,watch/providers") // 출연진/제작진, OTT 정보 포함
                .toUriString();
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("Error fetching movie details for ID {}: {}", movieId, e.getMessage());
            return null;
        }
    }

    /**
     * 특정 ID의 TV쇼 상세 정보를 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/tv-series-details
     *
     * @param tvId     TMDB TV쇼 ID
     * @param language 언어 코드
     * @return TV쇼 상세 정보가 담긴 Map
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTvShowDetails(int tvId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/tv/" + tvId)
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("append_to_response", "credits,watch/providers") // 출연진/제작진, OTT 정보 포함
                .toUriString();
        try {
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("Error fetching TV show details for ID {}: {}", tvId, e.getMessage());
            return null;
        }
    }
}