package com.example.crawler.ranking.steam.controller;

import com.example.crawler.ranking.steam.service.SteamRankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings/steam")
@RequiredArgsConstructor
public class SteamRankingController {

    private final SteamRankingService steamRankingService;

    @PostMapping("/topsellers/update")
    public ResponseEntity<String> updateTopSellersRanking() {
        steamRankingService.updateTopSellersRanking();
        return ResponseEntity.ok("Steam 최고 판매 랭킹 업데이트가 완료되었습니다.");
    }
}


