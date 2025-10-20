package com.example.AOD.contents.TMDB.fetcher;

import com.example.AOD.contents.TMDB.dto.TmdbDiscoveryResult;
import com.example.AOD.contents.TMDB.dto.TmdbMovie;
import com.example.AOD.contents.TMDB.dto.TmdbTvDiscoveryResult;
import com.example.AOD.contents.TMDB.dto.TmdbTvShow;
import com.example.AOD.contents.TMDB.dto.WatchProviderResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class TmdbApiFetcher {

    private final RestTemplate restTemplate;

    @Value("${tmdb.api.key}")
    private String apiKey;

    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String KOREAN_REGION = "KR";

    public TmdbApiFetcher() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * [개선] 영화 검색 API를 통합하여 호출합니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/discover-movie
     *
     * @param language  언어
     * @param page      페이지 번호
     * @param startDate 검색 시작일 (null 가능, 'primary_release_date.gte')
     * @param endDate   검색 종료일 (null 가능, 'primary_release_date.lte')
     * @return TmdbDiscoveryResult 객체
     */
    public TmdbDiscoveryResult discoverMovies(String language, int page, String startDate, String endDate) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/movie")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("region", KOREAN_REGION)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("page", page);

        if (startDate != null && !startDate.isEmpty()) {
            builder.queryParam("primary_release_date.gte", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            builder.queryParam("primary_release_date.lte", endDate);
        }

        String url = builder.toUriString();
        return restTemplate.getForObject(url, TmdbDiscoveryResult.class);
    }

    /**
     * [개선] TV쇼 검색 API를 통합하여 호출합니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/discover-tv
     *
     * @param language  언어
     * @param page      페이지 번호
     * @param startDate 검색 시작일 (null 가능, 'first_air_date.gte')
     * @param endDate   검색 종료일 (null 가능, 'first_air_date.lte')
     * @return TmdbTvDiscoveryResult 객체
     */
    public TmdbTvDiscoveryResult discoverTvShows(String language, int page, String startDate, String endDate) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/tv")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("region", KOREAN_REGION)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("page", page);

        if (startDate != null && !startDate.isEmpty()) {
            builder.queryParam("first_air_date.gte", startDate);
        }
        if (endDate != null && !endDate.isEmpty()) {
            builder.queryParam("first_air_date.lte", endDate);
        }

        String url = builder.toUriString();
        return restTemplate.getForObject(url, TmdbTvDiscoveryResult.class);
    }

    /**
     * 특정 ID의 영화 상세 정보를 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/movie-details
     *
     * @param movieId TMDB 영화 ID
     * @param language 언어 코드
     * @return TmdbMovie 객체
     */
    public TmdbMovie getMovieDetails(int movieId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/movie/" + movieId)
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("append_to_response", "credits") // 출연진/제작진 정보 포함
                .toUriString();
        try {
            return restTemplate.getForObject(url, TmdbMovie.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 특정 ID의 TV쇼 상세 정보를 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/tv-series-details
     *
     * @param tvId TMDB TV쇼 ID
     * @param language 언어 코드
     * @return TmdbTvShow 객체
     */
    public TmdbTvShow getTvShowDetails(int tvId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/tv/" + tvId)
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("append_to_response", "credits") // 출연진/제작진 정보 포함
                .toUriString();
        try {
            return restTemplate.getForObject(url, TmdbTvShow.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 영화의 스트리밍 서비스 정보를 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/movie-watch-providers
     *
     * @param movieId TMDB 영화 ID
     * @return WatchProviderResult 객체
     */
    public WatchProviderResult getWatchProviders(int movieId) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/movie/" + movieId + "/watch/providers")
                .queryParam("api_key", apiKey)
                .toUriString();
        try {
            return restTemplate.getForObject(url, WatchProviderResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * TV쇼의 스트리밍 서비스 정보를 가져옵니다.
     * TMDB API Reference: https://developer.themoviedb.org/reference/tv-series-watch-providers
     *
     * @param tvId TMDB TV쇼 ID
     * @return WatchProviderResult 객체
     */
    public WatchProviderResult getTvShowWatchProviders(int tvId) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/tv/" + tvId + "/watch/providers")
                .queryParam("api_key", apiKey)
                .toUriString();
        try {
            return restTemplate.getForObject(url, WatchProviderResult.class);
        } catch (Exception e) {
            return null;
        }
    }
}