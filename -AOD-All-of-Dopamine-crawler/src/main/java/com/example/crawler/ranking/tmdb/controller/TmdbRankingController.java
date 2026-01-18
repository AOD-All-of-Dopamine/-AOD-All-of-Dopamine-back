package com.example.crawler.ranking.tmdb.controller;

import com.example.crawler.ranking.tmdb.service.TmdbRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings/tmdb")
@RequiredArgsConstructor
public class TmdbRankingController {

    private final TmdbRankingService tmdbRankingService;

    @PostMapping("/movies/popular/update")
    public ResponseEntity<String> updatePopularMoviesRanking(
            @RequestParam(defaultValue = "100") int minVoteCount) {
        tmdbRankingService.updatePopularMoviesRanking(minVoteCount);
        return ResponseEntity.ok("TMDB 인기 영화 랭킹 업데이트가 시작되었습니다. (최소 투표수: " + minVoteCount + ")");
    }

    @PostMapping("/tv/popular/update")
    public ResponseEntity<String> updatePopularTvShowsRanking(
            @RequestParam(defaultValue = "100") int minVoteCount) {
        tmdbRankingService.updatePopularTvShowsRanking(minVoteCount);
        return ResponseEntity.ok("TMDB 인기 TV 쇼 랭킹 업데이트가 시작되었습니다. (최소 투표수: " + minVoteCount + ")");
    }
}


