package com.example.AOD.ranking.controller;

import com.example.AOD.ranking.entity.ExternalRanking;
import com.example.AOD.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @GetMapping("/{platform}")
    public ResponseEntity<List<ExternalRanking>> getRankings(@PathVariable String platform) {
        List<ExternalRanking> rankings = rankingService.getRankingsByPlatform(platform);
        return ResponseEntity.ok(rankings);
    }
}
