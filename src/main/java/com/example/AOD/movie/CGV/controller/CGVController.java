package com.example.AOD.movie.CGV.controller;

import com.example.AOD.movie.CGV.service.CGVService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
}