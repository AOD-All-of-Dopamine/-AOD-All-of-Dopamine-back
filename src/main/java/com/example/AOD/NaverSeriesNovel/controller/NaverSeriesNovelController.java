package com.example.AOD.NaverSeriesNovel.controller;


import com.example.AOD.NaverSeriesNovel.service.NaverSeriesService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@Controller
public class NaverSeriesNovelController {

    private final NaverSeriesService naverSeriesService;

    public NaverSeriesNovelController(NaverSeriesService naverSeriesService) {
        this.naverSeriesService = naverSeriesService;
    }

    @GetMapping("/novelStartCrawl")
    public ResponseEntity<Map<String,Object>> startCrawl() throws InterruptedException, AWTException {
        naverSeriesService.NaverSeriesNovelCrawl();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "크롤링 작업이 백그라운드에서 실행 중입니다.");
        return org.springframework.http.ResponseEntity.ok(response);
    }
}
