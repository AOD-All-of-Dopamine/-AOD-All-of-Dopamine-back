package com.example.AOD.TMDB.fetcher;

import com.example.AOD.TMDB.dto.TmdbDiscoveryResult;
import com.example.AOD.TMDB.dto.TmdbMovie;
import com.example.AOD.TMDB.dto.TmdbTvDiscoveryResult;
import com.example.AOD.TMDB.dto.TmdbTvShow;
import com.example.AOD.TMDB.dto.WatchProviderResult;
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
     * [추가] 특정 ID의 영화 상세 정보를 가져옵니다.
     * @param movieId TMDB 영화 ID
     * @param language 언어 코드
     * @return TmdbMovie 객체
     */
    public TmdbMovie getMovieDetails(int movieId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/movie/" + movieId)
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .toUriString();
        try {
            return restTemplate.getForObject(url, TmdbMovie.class);
        } catch (Exception e) {
            return null; // ID에 해당하는 영화가 없는 등 예외 발생 시 null 반환
        }
    }

    /**
     * [추가] 특정 ID의 TV쇼 상세 정보를 가져옵니다.
     * @param tvId TMDB TV쇼 ID
     * @param language 언어 코드
     * @return TmdbTvShow 객체
     */
    public TmdbTvShow getTvShowDetails(int tvId, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/tv/" + tvId)
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .toUriString();
        try {
            return restTemplate.getForObject(url, TmdbTvShow.class);
        } catch (Exception e) {
            return null; // ID에 해당하는 TV쇼가 없는 등 예외 발생 시 null 반환
        }
    }

    public TmdbDiscoveryResult discoverPopularMovies(int page, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/movie")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("region", KOREAN_REGION)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("page", page)
                .toUriString();

        return restTemplate.getForObject(url, TmdbDiscoveryResult.class);
    }

    public TmdbDiscoveryResult discoverMoviesByDateRange(int page, String language, String startDate, String endDate) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/movie")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("region", KOREAN_REGION)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("primary_release_date.gte", startDate)
                .queryParam("primary_release_date.lte", endDate)
                .queryParam("page", page)
                .toUriString();
        return restTemplate.getForObject(url, TmdbDiscoveryResult.class);
    }

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

    public TmdbTvDiscoveryResult discoverPopularTvShows(int page, String language) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/tv")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("region", KOREAN_REGION)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("page", page)
                .toUriString();

        return restTemplate.getForObject(url, TmdbTvDiscoveryResult.class);
    }

    public TmdbTvDiscoveryResult discoverTvShowsByDateRange(int page, String language, String startDate, String endDate) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL + "/discover/tv")
                .queryParam("api_key", apiKey)
                .queryParam("language", language)
                .queryParam("region", KOREAN_REGION)
                .queryParam("sort_by", "popularity.desc")
                .queryParam("first_air_date.gte", startDate)
                .queryParam("first_air_date.lte", endDate)
                .queryParam("page", page)
                .toUriString();
        return restTemplate.getForObject(url, TmdbTvDiscoveryResult.class);
    }

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

