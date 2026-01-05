package com.example.crawler.game.steam.controller;

import com.example.crawler.game.steam.service.SteamCrawlService;
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
     * 
     * @return 작업 시작 확인 메시지
     */
    @PostMapping("/collect/all-games")
    public ResponseEntity<Map<String, String>> collectAllGames() {
        steamCrawlService.collectAllGamesInBatches();
        return ResponseEntity.ok(Map.of("message", "모든 Steam 게임 데이터 수집 작업을 시작합니다. (1000개씩 자동 분할 처리)"));
    }

    /**
     * 특정 AppID의 Steam 게임 상세 정보를 수집하여 저장합니다.
     * Admin 페이지에서 사용하기 위한 개별 게임 수집 API입니다.
     * 
     * @param request appId를 포함한 요청 본문
     * @return 수집 결과 메시지
     */
    @PostMapping("/collect/by-appid")
    public ResponseEntity<Map<String, Object>> collectGameByAppId(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> request) {
        try {
            Long appId = ((Number) request.get("appId")).longValue();
            boolean success = steamCrawlService.collectGameByAppId(appId);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Steam 게임 AppID " + appId + "의 데이터 수집에 성공했습니다.",
                    "appId", appId
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Steam 게임 AppID " + appId + "의 데이터를 가져올 수 없거나 게임이 아닙니다.",
                    "appId", appId
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "요청 처리 중 오류 발생: " + e.getMessage()
            ));
        }
    }
}

