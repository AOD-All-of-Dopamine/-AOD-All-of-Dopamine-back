package com.example.AOD.movie.CGV.controller;

import com.example.AOD.movie.CGV.service.CGVService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class CGVController {

    private final CGVService cgvService;

    public CGVController(CGVService cgvService) {
        this.cgvService = cgvService;
    }

    @GetMapping("/startCGVCrawl")
    public ResponseEntity<Map<String, Object>> startCrawl() {
        cgvService.crawlMovies();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "CGV 크롤링 작업이 백그라운드에서 실행 중입니다.");

        return ResponseEntity.ok(response);
    }
    /**
     * 수동으로 일일 크롤링을 트리거하는 엔드포인트
     * 테스트 및 디버깅 용도
     */
    @GetMapping("/triggerDailyCGVCrawl")
    public ResponseEntity<Map<String, Object>> triggerDailyMovieCrawl() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("message", "CGV 일일 크롤링이 수동으로 트리거되었습니다.");

        // 비동기로 실행하지 않고 동기적으로 실행
        cgvService.scheduledDailyCrawl();

        return ResponseEntity.ok(response);
    }
}