package com.example.crawler.ranking.Webnovel.NaverSeries.controller;

import com.example.crawler.ranking.Webnovel.NaverSeries.service.NaverSeriesRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네이버 시리즈(웹소설) 랭킹 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/rankings/naver/series")
@RequiredArgsConstructor
public class NaverSeriesRankingController {

    private final NaverSeriesRankingService rankingService;

    /**
     * 일간 네이버 시리즈 랭킹 업데이트
     * POST /api/rankings/naver/series/daily/update
     */
    @PostMapping("/daily/update")
    public ResponseEntity<String> updateDailyRanking() {
        try {
            rankingService.updateDailyRanking();
            return ResponseEntity.ok("네이버 시리즈 일간 랭킹 업데이트 완료");
        } catch (Exception e) {
            log.error("네이버 시리즈 랭킹 업데이트 실패", e);
            return ResponseEntity.internalServerError()
                    .body("랭킹 업데이트 실패: " + e.getMessage());
        }
    }
}


