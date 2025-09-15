// src/main/java/com/example/AOD/TMDB/controller/TmdbTestController.java

package com.example.AOD.TMDB.controller;

import com.example.AOD.TMDB.dto.TmdbDiscoveryResult;
import com.example.AOD.TMDB.dto.TmdbTvDiscoveryResult;
import com.example.AOD.TMDB.dto.WatchProviderResult.CountryProviders;
import com.example.AOD.TMDB.fetcher.TmdbApiFetcher;
import com.example.AOD.TMDB.service.TmdbService;
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

    // --- [신규] 연도별 샘플 데이터 수집 API ---
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


    // --- API 응답 미리보기 API ---

    @GetMapping("/preview/discover/movie")
    public ResponseEntity<TmdbDiscoveryResult> previewDiscoverMovies(@RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(tmdbApiFetcher.discoverPopularMovies(page, "ko-KR"));
    }

    @GetMapping("/preview/discover/tv")
    public ResponseEntity<TmdbTvDiscoveryResult> previewDiscoverTvShows(@RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(tmdbApiFetcher.discoverPopularTvShows(page, "ko-KR"));
    }

    @GetMapping("/preview/movie/{movieId}/providers")
    public ResponseEntity<CountryProviders> previewKoreanWatchProviders(@PathVariable int movieId) {
        var fullResult = tmdbApiFetcher.getWatchProviders(movieId);
        if (fullResult != null && fullResult.getResults() != null) {
            return ResponseEntity.ok(fullResult.getResults().get("KR"));
        }
        return ResponseEntity.ok(null);
    }

    @GetMapping("/preview/tv/{tvId}/providers")
    public ResponseEntity<CountryProviders> previewKoreanTvShowWatchProviders(@PathVariable int tvId) {
        var fullResult = tmdbApiFetcher.getTvShowWatchProviders(tvId);
        if (fullResult != null && fullResult.getResults() != null) {
            return ResponseEntity.ok(fullResult.getResults().get("KR"));
        }
        return ResponseEntity.ok(null);
    }
}