// src/main/java/com/example/AOD/TMDB/controller/TmdbTestController.java

package com.example.crawler.contents.TMDB.controller;

import com.example.crawler.contents.TMDB.dto.TmdbDiscoveryResult;
import com.example.crawler.contents.TMDB.dto.TmdbTvDiscoveryResult;
import com.example.crawler.contents.TMDB.fetcher.TmdbApiFetcher;
import com.example.crawler.contents.TMDB.service.TmdbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test/tmdb")
@RequiredArgsConstructor
public class TmdbTestController {

    private final TmdbApiFetcher tmdbApiFetcher;
    private final TmdbService tmdbService;

    // --- 샘플 데이터 수집 API ---

    /**
     * TMDB 인기 영화 데이터를 지정된 페이지 수만큼 시험적으로 수집합니다.
     * @param pages 수집할 페이지 수 (기본값: 10)
     * @return 작업 시작 확인 메시지
     */
    @PostMapping("/collect/popular-movies")
    public ResponseEntity<Map<String, String>> startPopularMoviesSampleCollection(
            @RequestParam(defaultValue = "10") int pages) {

        tmdbService.collectPopularMovies(pages, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB 인기 영화 샘플 데이터 " + pages + " 페이지 수집을 시작합니다."));
    }

    /**
     * TMDB 인기 TV쇼 데이터를 지정된 페이지 수만큼 시험적으로 수집합니다.
     * @param pages 수집할 페이지 수 (기본값: 10)
     * @return 작업 시작 확인 메시지
     */
    @PostMapping("/collect/popular-tv")
    public ResponseEntity<Map<String, String>> startPopularTvShowsSampleCollection(
            @RequestParam(defaultValue = "10") int pages) {

        tmdbService.collectPopularTvShows(pages, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB 인기 TV쇼 샘플 데이터 " + pages + " 페이지 수집을 시작합니다."));
    }

    @PostMapping("/collect/movies-by-year/sample")
    public ResponseEntity<Map<String, String>> startMoviesByYearSampleCollection(
            @RequestParam int year,
            @RequestParam(defaultValue = "2") int pages) {

        tmdbService.collectMoviesByYearSample(year, pages, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB " + year + "년 영화 샘플 데이터 " + pages + " 페이지 수집을 시작합니다."));
    }

    @PostMapping("/collect/tv-by-year/sample")
    public ResponseEntity<Map<String, String>> startTvShowsByYearSampleCollection(
            @RequestParam int year,
            @RequestParam(defaultValue = "2") int pages) {

        tmdbService.collectTvShowsByYearSample(year, pages, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB " + year + "년 TV쇼 샘플 데이터 " + pages + " 페이지 수집을 시작합니다."));
    }


    // --- API 응답 미리보기 API (개선된 부분) ---

    @GetMapping("/preview/discover/movie")
    public ResponseEntity<TmdbDiscoveryResult> previewDiscoverMovies(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String year) {

        String startDate = (year != null) ? year + "-01-01" : null;
        String endDate = (year != null) ? year + "-12-31" : null;

        // [개선] 통합된 discoverMovies 메서드 사용
        return ResponseEntity.ok(tmdbApiFetcher.discoverMovies("ko-KR", page, startDate, endDate));
    }

    @GetMapping("/preview/discover/tv")
    public ResponseEntity<TmdbTvDiscoveryResult> previewDiscoverTvShows(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String year) {

        String startDate = (year != null) ? year + "-01-01" : null;
        String endDate = (year != null) ? year + "-12-31" : null;

        // [개선] 통합된 discoverTvShows 메서드 사용
        return ResponseEntity.ok(tmdbApiFetcher.discoverTvShows("ko-KR", page, startDate, endDate));
    }

    @GetMapping("/preview/movie/{movieId}/providers")
    public ResponseEntity<Object> previewKoreanWatchProviders(@PathVariable int movieId) {
        Map<String, Object> movieDetails = tmdbApiFetcher.getMovieDetails(movieId, "ko-KR");
        return extractKoreanProviders(movieDetails);
    }

    @GetMapping("/preview/tv/{tvId}/providers")
    public ResponseEntity<Object> previewKoreanTvShowWatchProviders(@PathVariable int tvId) {
        Map<String, Object> tvShowDetails = tmdbApiFetcher.getTvShowDetails(tvId, "ko-KR");
        return extractKoreanProviders(tvShowDetails);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Object> extractKoreanProviders(Map<String, Object> details) {
        if (details != null && details.containsKey("watch/providers")) {
            Map<String, Object> providers = (Map<String, Object>) details.get("watch/providers");
            if (providers != null && providers.containsKey("results")) {
                Map<String, Object> results = (Map<String, Object>) providers.get("results");
                return ResponseEntity.ok(results.get("KR"));
            }
        }
        return ResponseEntity.ok(null);
    }
}

