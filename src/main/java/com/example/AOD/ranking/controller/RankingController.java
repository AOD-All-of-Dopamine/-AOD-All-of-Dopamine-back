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

    /**
     * 모든 플랫폼의 랭킹을 실시간으로 크롤링하여 반환합니다.
     * 크롤링 완료 후 DB에 저장된 전체 랭킹 데이터를 반환합니다.
     */
    @GetMapping("/all")
    public ResponseEntity<List<ExternalRanking>> crawlAllRankings() {
        List<ExternalRanking> rankings = rankingService.crawlAndGetAllRankings();
        return ResponseEntity.ok(rankings);
    }

    /**
     * 특정 플랫폼의 랭킹을 조회합니다 (DB에 저장된 데이터 조회).
     */
    @GetMapping("/{platform}")
    public ResponseEntity<List<ExternalRanking>> getRankings(@PathVariable String platform) {
        List<ExternalRanking> rankings = rankingService.getRankingsByPlatform(platform);
        return ResponseEntity.ok(rankings);
    }
}
