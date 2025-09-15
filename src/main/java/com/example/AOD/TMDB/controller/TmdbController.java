package com.example.AOD.TMDB.controller;

import com.example.AOD.TMDB.service.TmdbService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Year;
import java.util.Map;

@RestController
@RequestMapping("/api/crawl/tmdb")
@RequiredArgsConstructor
public class TmdbController {

    private final TmdbService tmdbService;

    // --- 전체 데이터 수집 API ---
    @PostMapping("/movies-by-year")
    public ResponseEntity<Map<String, String>> startMoviesCollectionByYear(
            @RequestParam(defaultValue = "0") int startYear,
            @RequestParam(defaultValue = "1980") int endYear) {

        int effectiveStartYear = (startYear == 0) ? Year.now().getValue() : startYear;
        tmdbService.collectAllMoviesByYear(effectiveStartYear, endYear, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB " + endYear + "년부터 " + effectiveStartYear + "년까지의 영화 데이터 수집을 시작합니다."));
    }

    @PostMapping("/tv-by-year")
    public ResponseEntity<Map<String, String>> startTvShowsCollectionByYear(
            @RequestParam(defaultValue = "0") int startYear,
            @RequestParam(defaultValue = "1980") int endYear) {

        int effectiveStartYear = (startYear == 0) ? Year.now().getValue() : startYear;
        tmdbService.collectAllTvShowsByYear(effectiveStartYear, endYear, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB " + endYear + "년부터 " + effectiveStartYear + "년까지의 TV쇼 데이터 수집을 시작합니다."));
    }

    // --- [NEW] 샘플 데이터 수집 API ---
    /**
     * TMDB 인기 영화 데이터를 지정된 페이지 수만큼 시험적으로 수집합니다.
     * @param pages 수집할 페이지 수 (기본값: 10)
     * @return 작업 시작 확인 메시지
     */
    @PostMapping("/popular-movies/sample")
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
    @PostMapping("/popular-tv/sample")
    public ResponseEntity<Map<String, String>> startPopularTvShowsSampleCollection(
            @RequestParam(defaultValue = "10") int pages) {

        tmdbService.collectPopularTvShows(pages, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB 인기 TV쇼 샘플 데이터 " + pages + " 페이지 수집을 시작합니다."));
    }
}

