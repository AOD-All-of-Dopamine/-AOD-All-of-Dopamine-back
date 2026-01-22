// src/main/java/com/example/AOD/TMDB/controller/TmdbController.java

package com.example.crawler.contents.TMDB.controller;

import com.example.crawler.contents.TMDB.service.TmdbService;
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

    /**
     * 지정된 기간 동안의 모든 영화 데이터를 TMDB에서 수집합니다.
     * 
     * @deprecated Job Queue 패턴 사용 권장 - AdminTestController의 /api/crawl/tmdb/movies-by-year 사용
     * 이 엔드포인트는 직접 크롤링 방식으로 동기 처리되어 서버 부하 발생 가능
     */
    @Deprecated
    @PostMapping("/movies-by-year-direct")
    public ResponseEntity<Map<String, String>> startMoviesCollectionByYear(
            @RequestParam(defaultValue = "0") int startYear,
            @RequestParam(defaultValue = "1980") int endYear) {

        int effectiveStartYear = (startYear == 0) ? Year.now().getValue() : startYear;
        tmdbService.collectAllMoviesByYear(effectiveStartYear, endYear, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB " + endYear + "년부터 " + effectiveStartYear + "년까지의 영화 데이터 수집을 시작합니다."));
    }

    /**
     * 지정된 기간 동안의 모든 TV쇼 데이터를 TMDB에서 수집합니다.
     * 
     * @deprecated Job Queue 패턴 사용 권장 - AdminTestController의 /api/crawl/tmdb/tv-by-year 사용
     * 이 엔드포인트는 직접 크롤링 방식으로 동기 처리되어 서버 부하 발생 가능
     */
    @Deprecated
    @PostMapping("/tv-by-year-direct")
    public ResponseEntity<Map<String, String>> startTvShowsCollectionByYear(
            @RequestParam(defaultValue = "0") int startYear,
            @RequestParam(defaultValue = "1980") int endYear) {

        int effectiveStartYear = (startYear == 0) ? Year.now().getValue() : startYear;
        tmdbService.collectAllTvShowsByYear(effectiveStartYear, endYear, "ko-KR");
        return ResponseEntity.ok(Map.of("message", "TMDB " + endYear + "년부터 " + effectiveStartYear + "년까지의 TV쇼 데이터 수집을 시작합니다."));
    }
}

