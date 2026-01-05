package com.example.AOD.ranking.controller;

import com.example.AOD.ranking.dto.RankingResponse;
import com.example.shared.entity.ExternalRanking;
import com.example.AOD.ranking.mapper.RankingMapper;
import com.example.AOD.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 랭킹 조회 API 컨트롤러 (API 서버)
 * - 크롤링은 크롤러 서버에서 담당
 * - API 서버는 조회만 제공
 * 
 * 엔드포인트:
 * - GET /api/rankings/all: 전체 랭킹 조회
 * - GET /api/rankings/{platform}: 플랫폼별 랭킹 조회
 * - GET /api/rankings/domain/{domain}: 도메인별 랭킹 조회
 */
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final RankingMapper rankingMapper;

    /**
     * 전체 랭킹 조회 (DB에서 가져오기)
     * 
     * @return 전체 랭킹 리스트
     */
    @GetMapping("/all")
    public ResponseEntity<List<RankingResponse>> getAllRankings() {
        List<ExternalRanking> rankings = rankingService.getAllRankings();
        return ResponseEntity.ok(rankingMapper.toResponseList(rankings));
    }

    /**
     * 플랫폼별 랭킹 조회
     * 
     * @param platform 플랫폼 이름 (NaverWebtoon, NaverSeries, Steam, TMDB_MOVIE, TMDB_TV)
     * @return 해당 플랫폼의 랭킹 리스트
     */
    @GetMapping("/{platform}")
    public ResponseEntity<List<RankingResponse>> getRankingsByPlatform(@PathVariable String platform) {
        List<ExternalRanking> rankings = rankingService.getRankingsByPlatform(platform);
        return ResponseEntity.ok(rankingMapper.toResponseList(rankings));
    }

    /**
     * 도메인별 랭킹 조회
     * 
     * @param domain 도메인 (MOVIE, TV, GAME, WEBTOON, WEBNOVEL)
     * @return 해당 도메인의 랭킹 리스트
     */
    @GetMapping("/domain/{domain}")
    public ResponseEntity<List<RankingResponse>> getRankingsByDomain(@PathVariable String domain) {
        List<ExternalRanking> rankings = rankingService.getRankingsByDomain(domain);
        return ResponseEntity.ok(rankingMapper.toResponseList(rankings));
    }
}
