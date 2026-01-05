package com.example.crawler.ranking.tmdb.controller;

import com.example.crawler.ranking.tmdb.service.TmdbRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings/tmdb")
@RequiredArgsConstructor
public class TmdbRankingController {

    private final TmdbRankingService tmdbRankingService;

    @PostMapping("/movies/popular/update")
    public ResponseEntity<String> updatePopularMoviesRanking() {
        tmdbRankingService.updatePopularMoviesRanking();
        return ResponseEntity.ok("TMDB 인기 영화 랭킹 업데이트가 시작되었습니다.");
    }

    @PostMapping("/tv/popular/update")
    public ResponseEntity<String> updatePopularTvShowsRanking() {
        tmdbRankingService.updatePopularTvShowsRanking();
        return ResponseEntity.ok("TMDB 인기 TV 쇼 랭킹 업데이트가 시작되었습니다.");
    }
}


