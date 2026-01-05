package com.example.crawler.ranking.Webtoon.NaverWebtoon.controller;

import com.example.crawler.ranking.Webtoon.NaverWebtoon.service.NaverWebtoonRankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 네이버 웹툰 랭킹 컨트롤러 (리팩토링됨)
 * - 기존 NaverWebtoonCrawler 재사용으로 중복 제거
 */
@Slf4j
@RestController
@RequestMapping("/api/rankings/naver/webtoon")
@RequiredArgsConstructor
public class NaverWebtoonRankingController {

    private final NaverWebtoonRankingService rankingService;

    /**
     * 오늘 요일의 네이버 웹툰 랭킹 업데이트
     * POST /api/rankings/naver/webtoon/today/update
     * 
     * 주의: 기존 NaverWebtoonCrawler의 공통 메서드를 재사용
     */
    @PostMapping("/today/update")
    public ResponseEntity<String> updateTodayRanking() {
        try {
            rankingService.updateTodayWebtoonRanking();
            return ResponseEntity.ok("네이버 웹툰 오늘 요일 랭킹 업데이트 완료");
        } catch (Exception e) {
            log.error("네이버 웹툰 랭킹 업데이트 실패", e);
            return ResponseEntity.internalServerError()
                    .body("랭킹 업데이트 실패: " + e.getMessage());
        }
    }
}


