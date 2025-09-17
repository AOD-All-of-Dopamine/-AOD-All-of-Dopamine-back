package com.example.AOD.game.steam.controller;

import com.example.AOD.game.steam.service.SteamCrawlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/steam")
@RequiredArgsConstructor
public class SteamController {

    private final SteamCrawlService steamCrawlService;

    /**
     * (메인) 모든 Steam 게임의 상세 정보를 수집하는 전체 프로세스를 시작합니다.
     * 내부적으로 1000개 단위로 작업을 분할하여 순차적으로 수집을 진행합니다.
     * @return 작업 시작 확인 메시지
     */
    @PostMapping("/collect/all-games")
    public ResponseEntity<Map<String, String>> collectAllGames() {
        steamCrawlService.collectAllGamesInBatches();
        return ResponseEntity.ok(Map.of("message", "모든 Steam 게임 데이터 수집 작업을 시작합니다. (1000개씩 자동 분할 처리)"));
    }
}