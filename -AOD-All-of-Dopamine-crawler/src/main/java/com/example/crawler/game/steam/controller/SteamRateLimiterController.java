package com.example.crawler.game.steam.controller;

import com.example.crawler.game.steam.util.SteamRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Steam Rate Limiter 모니터링 및 관리 API
 */
@RestController
@RequestMapping("/api/steam/rate-limiter")
@RequiredArgsConstructor
public class SteamRateLimiterController {

    private final SteamRateLimiter rateLimiter;

    /**
     * Rate Limiter 현재 상태 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        String stats = rateLimiter.getStats();
        return ResponseEntity.ok(Map.of(
            "stats", stats,
            "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Rate Limiter 초기화
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        rateLimiter.reset();
        return ResponseEntity.ok(Map.of(
            "message", "Rate Limiter가 초기화되었습니다.",
            "stats", rateLimiter.getStats()
        ));
    }

    /**
     * Rate Limiter 테스트용 - 10개 연속 요청 시뮬레이션
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testRateLimiter() {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 10; i++) {
            rateLimiter.acquirePermit();
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        return ResponseEntity.ok(Map.of(
            "message", "10개 요청 완료",
            "elapsedTimeMs", elapsedTime,
            "elapsedTimeSec", elapsedTime / 1000.0,
            "stats", rateLimiter.getStats()
        ));
    }

    /**
     * Rate Limiter 테스트용 - 250개 연속 요청 시뮬레이션
     * 예상 소요 시간: 약 62~65초 (초당 8개 + 분당 120개 제한)
     * - 첫 60초: 120개 처리 (분당 제한)
     * - 이후: 130개 추가 처리 (약 2~3초 더 소요)
     */
    @PostMapping("/test/250")
    public ResponseEntity<Map<String, Object>> testRateLimiter250() {
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < 250; i++) {
            rateLimiter.acquirePermit();
        }
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        return ResponseEntity.ok(Map.of(
            "message", "250개 요청 완료",
            "elapsedTimeMs", elapsedTime,
            "elapsedTimeSec", elapsedTime / 1000.0,
            "stats", rateLimiter.getStats()
        ));
    }
}


