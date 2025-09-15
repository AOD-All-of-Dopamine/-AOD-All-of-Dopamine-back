package com.example.AOD.TMDB.controller;

import com.example.AOD.TMDB.dto.*;
import com.example.AOD.TMDB.fetcher.TmdbApiFetcher;
import com.example.AOD.domain.entity.Domain;
import com.example.AOD.rules.MappingRule;
import com.example.AOD.service.RuleLoader;
import com.example.AOD.service.TransformEngine;
import com.example.AOD.service.UpsertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.example.AOD.TMDB.dto.WatchProviderResult.CountryProviders;

@RestController
@RequestMapping("/api/test/tmdb")
@RequiredArgsConstructor
public class TmdbTestController {

    private final TmdbApiFetcher tmdbApiFetcher;
    private final RuleLoader ruleLoader;
    private final TransformEngine transformEngine;
    private final UpsertService upsertService;
    private final ObjectMapper objectMapper;

    // --- API 미리보기 엔드포인트들 ---

    @GetMapping("/discover/movie")
    public ResponseEntity<TmdbDiscoveryResult> testDiscoverMovies(@RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(tmdbApiFetcher.discoverPopularMovies(page, "ko-KR"));
    }

    @GetMapping("/discover/movie-by-year")
    public ResponseEntity<TmdbDiscoveryResult> testDiscoverMoviesByYear(@RequestParam int year, @RequestParam(defaultValue = "1") int page) {
        String startDate = year + "-01-01";
        String endDate = year + "-12-31";
        return ResponseEntity.ok(tmdbApiFetcher.discoverMoviesByDateRange(page, "ko-KR", startDate, endDate));
    }

    @GetMapping("/movie/{movieId}/providers/kr")
    public ResponseEntity<CountryProviders> testKoreanWatchProviders(@PathVariable int movieId) {
        WatchProviderResult fullResult = tmdbApiFetcher.getWatchProviders(movieId);
        if (fullResult != null && fullResult.getResults() != null) {
            return ResponseEntity.ok(fullResult.getResults().get("KR"));
        }
        return ResponseEntity.ok(null);
    }

    @GetMapping("/discover/tv")
    public ResponseEntity<TmdbTvDiscoveryResult> testDiscoverTvShows(@RequestParam(defaultValue = "1") int page) {
        return ResponseEntity.ok(tmdbApiFetcher.discoverPopularTvShows(page, "ko-KR"));
    }

    @GetMapping("/tv/{tvId}/providers/kr")
    public ResponseEntity<CountryProviders> testKoreanTvShowWatchProviders(@PathVariable int tvId) {
        WatchProviderResult fullResult = tmdbApiFetcher.getTvShowWatchProviders(tvId);
        if (fullResult != null && fullResult.getResults() != null) {
            return ResponseEntity.ok(fullResult.getResults().get("KR"));
        }
        return ResponseEntity.ok(null);
    }

    // --- E2E 파이프라인 테스트 엔드포인트 ---

    @PostMapping("/process-movie/{movieId}")
    public ResponseEntity<Map<String, Object>> processSingleTmdbMovie(@PathVariable int movieId) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            // 1. 수집
            TmdbMovie movie = tmdbApiFetcher.getMovieDetails(movieId, "ko-KR");
            if (movie == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Movie not found with ID: " + movieId));
            }
            WatchProviderResult providers = tmdbApiFetcher.getWatchProviders(movieId);
            Map<String, Object> rawPayload = new LinkedHashMap<>();
            rawPayload.put("movie_details", objectMapper.convertValue(movie, Map.class));
            if (providers != null) rawPayload.put("watch_providers", providers.getResults());
            response.put("1_collected_payload", rawPayload);

            // 2. 변환
            MappingRule rule = ruleLoader.load("rules/av/tmdb.yml");
            TransformEngine.Triple transformed = transformEngine.transform(rawPayload, rule);
            response.put("2_transformed_data", transformed);

            // 3. 업서트
            Long contentId = upsertService.upsert(
                    Domain.AV, transformed.master(), transformed.platform(), transformed.domain(),
                    String.valueOf(movieId), "https://www.themoviedb.org/movie/" + movieId
            );
            response.put("3_upserted_content_id", contentId);

            if (contentId != null) {
                response.put("message", "Successfully processed movie ID: " + movieId);
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Processing failed. Check logs.");
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/process-tv/{tvId}")
    public ResponseEntity<Map<String, Object>> processSingleTmdbTvShow(@PathVariable int tvId) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            // 1. 수집
            TmdbTvShow tvShow = tmdbApiFetcher.getTvShowDetails(tvId, "ko-KR");
            if (tvShow == null) {
                return ResponseEntity.status(404).body(Map.of("error", "TV show not found with ID: " + tvId));
            }
            WatchProviderResult providers = tmdbApiFetcher.getTvShowWatchProviders(tvId);
            Map<String, Object> rawPayload = new LinkedHashMap<>();
            rawPayload.put("tv_details", objectMapper.convertValue(tvShow, Map.class));
            if (providers != null) rawPayload.put("watch_providers", providers.getResults());
            response.put("1_collected_payload", rawPayload);

            // 2. 변환 (TV용 규칙 파일이 필요하지만, 일단 영화용 규칙으로 테스트)
            // TODO: Create and use 'rules/av/tmdb_tv.yml'
            MappingRule rule = ruleLoader.load("rules/av/tmdb.yml");
            TransformEngine.Triple transformed = transformEngine.transform(rawPayload, rule);
            response.put("2_transformed_data", transformed);

            // 3. 업서트
            Long contentId = upsertService.upsert(
                    Domain.AV, transformed.master(), transformed.platform(), transformed.domain(),
                    String.valueOf(tvId), "https://www.themoviedb.org/tv/" + tvId
            );
            response.put("3_upserted_content_id", contentId);

            if (contentId != null) {
                response.put("message", "Successfully processed TV show ID: " + tvId);
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "Processing failed. Check logs.");
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}

