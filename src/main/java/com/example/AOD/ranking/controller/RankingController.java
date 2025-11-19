package com.example.AOD.ranking.controller;

import com.example.AOD.ranking.dto.RankingResponse;
import com.example.AOD.ranking.entity.ExternalRanking;
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
 * 랭킹 API 컨트롤러
 * 
 * 엔드포인트:
 * - GET /api/rankings/all: 전체 플랫폼 랭킹 크롤링 및 조회
 * - GET /api/rankings/{platform}: 특정 플랫폼 랭킹 조회
 * 
 * 응답 타입:
 * - List<RankingResponse>
 *   - id: Long
 *   - contentId: Long (매핑된 경우만)
 *   - ranking: Integer
 *   - thumbnailUrl: String
 *   - content: ContentInfo (매핑된 경우만)
 */
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final RankingMapper rankingMapper;

    /**
     * 모든 플랫폼의 랭킹을 실시간으로 크롤링하여 반환
     * 
     * 실행 순서:
     * 1. 네이버 웹툰 (오늘 요일 기준)
     * 2. 네이버 시리즈 (웹소설 일간)
     * 3. Steam (최고 판매)
     * 4. TMDB (인기 영화)
     * 5. TMDB (인기 TV 쇼)
     * 
     * @return 전체 랭킹 리스트 (플랫폼별 정렬)
     */
    @GetMapping("/all")
    public ResponseEntity<List<RankingResponse>> crawlAllRankings() {
        List<ExternalRanking> rankings = rankingService.crawlAndGetAllRankings();
        return ResponseEntity.ok(rankingMapper.toResponseList(rankings));
    }

    /**
     * 특정 플랫폼의 랭킹 조회 (DB 캐시 데이터)
     * 
     * @param platform 플랫폼 이름 (NaverWebtoon, NaverSeries, Steam, TMDB_MOVIE, TMDB_TV)
     * @return 해당 플랫폼의 랭킹 리스트 (순위 오름차순)
     */
    @GetMapping("/{platform}")
    public ResponseEntity<List<RankingResponse>> getRankings(@PathVariable String platform) {
        List<ExternalRanking> rankings = rankingService.getRankingsByPlatform(platform);
        return ResponseEntity.ok(rankingMapper.toResponseList(rankings));
    }
}
