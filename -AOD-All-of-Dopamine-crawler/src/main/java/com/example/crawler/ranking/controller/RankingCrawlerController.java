package com.example.crawler.ranking.controller;

import com.example.crawler.ranking.service.RankingCrawlerService;
import com.example.shared.entity.ExternalRanking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 통합 랭킹 크롤링 컨트롤러 (Crawler 서버)
 * - 모든 플랫폼의 랭킹을 크롤링하는 API 제공
 * 
 * 엔드포인트:
 * - POST /api/crawler/rankings/all: 전체 플랫폼 랭킹 크롤링
 * - POST /api/crawler/rankings/naver-webtoon: 네이버 웹툰 랭킹만 크롤링
 * - POST /api/crawler/rankings/naver-series: 네이버 시리즈 랭킹만 크롤링
 * - POST /api/crawler/rankings/steam: Steam 랭킹만 크롤링
 * - POST /api/crawler/rankings/tmdb: TMDB 랭킹만 크롤링
 */
@Slf4j
@RestController
@RequestMapping("/api/crawler/rankings")
@RequiredArgsConstructor
public class RankingCrawlerController {

    private final RankingCrawlerService rankingCrawlerService;

    /**
     * 전체 플랫폼 랭킹 크롤링
     * POST /api/crawler/rankings/all
     * 
     * @return 크롤링된 전체 랭킹 리스트
     */
    @PostMapping("/all")
    public ResponseEntity<List<ExternalRanking>> crawlAllRankings() {
        try {
            log.info("전체 플랫폼 랭킹 크롤링 요청 수신");
            List<ExternalRanking> rankings = rankingCrawlerService.crawlAndGetAllRankings();
            return ResponseEntity.ok(rankings);
        } catch (Exception e) {
            log.error("전체 랭킹 크롤링 실패", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 네이버 웹툰 랭킹만 크롤링
     * POST /api/crawler/rankings/naver-webtoon
     */
    @PostMapping("/naver-webtoon")
    public ResponseEntity<String> crawlNaverWebtoonRanking() {
        try {
            rankingCrawlerService.crawlNaverWebtoonRanking();
            return ResponseEntity.ok("네이버 웹툰 랭킹 크롤링 완료");
        } catch (Exception e) {
            log.error("네이버 웹툰 랭킹 크롤링 실패", e);
            return ResponseEntity.internalServerError()
                    .body("크롤링 실패: " + e.getMessage());
        }
    }

    /**
     * 네이버 시리즈 랭킹만 크롤링
     * POST /api/crawler/rankings/naver-series
     */
    @PostMapping("/naver-series")
    public ResponseEntity<String> crawlNaverSeriesRanking() {
        try {
            rankingCrawlerService.crawlNaverSeriesRanking();
            return ResponseEntity.ok("네이버 시리즈 랭킹 크롤링 완료");
        } catch (Exception e) {
            log.error("네이버 시리즈 랭킹 크롤링 실패", e);
            return ResponseEntity.internalServerError()
                    .body("크롤링 실패: " + e.getMessage());
        }
    }

    /**
     * Steam 랭킹만 크롤링
     * POST /api/crawler/rankings/steam
     */
    @PostMapping("/steam")
    public ResponseEntity<String> crawlSteamRanking() {
        try {
            rankingCrawlerService.crawlSteamRanking();
            return ResponseEntity.ok("Steam 랭킹 크롤링 완료");
        } catch (Exception e) {
            log.error("Steam 랭킹 크롤링 실패", e);
            return ResponseEntity.internalServerError()
                    .body("크롤링 실패: " + e.getMessage());
        }
    }

    /**
     * TMDB 랭킹만 크롤링 (영화 + TV)
     * POST /api/crawler/rankings/tmdb
     */
    @PostMapping("/tmdb")
    public ResponseEntity<String> crawlTmdbRanking() {
        try {
            rankingCrawlerService.crawlTmdbRanking();
            return ResponseEntity.ok("TMDB 랭킹 크롤링 완료");
        } catch (Exception e) {
            log.error("TMDB 랭킹 크롤링 실패", e);
            return ResponseEntity.internalServerError()
                    .body("크롤링 실패: " + e.getMessage());
        }
    }
}
