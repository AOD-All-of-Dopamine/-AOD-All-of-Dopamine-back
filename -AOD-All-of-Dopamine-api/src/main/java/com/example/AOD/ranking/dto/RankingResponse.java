package com.example.AOD.ranking.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 랭킹 API 응답 DTO
 * - Hibernate Lazy Loading 문제 해결
 * - 프론트엔드 호환성을 위한 필드 구조
 * 
 * 타입 정책:
 * - id: Long (랭킹 데이터의 고유 ID)
 * - contentId: Long (매핑된 내부 작품 ID, null 가능)
 * - ranking: Integer (순위, 1부터 시작)
 * - thumbnailUrl: String (썸네일 이미지 URL, null 가능)
 */
@Getter
@Setter
public class RankingResponse {
    private Long id;                    // 랭킹 엔트리 고유 ID
    private Long contentId;             // 매핑된 Content ID (있는 경우), 프론트엔드 호환
    private String title;               // 작품 제목
    private Integer ranking;            // 순위 (1, 2, 3, ...)
    private String platform;            // 플랫폼 이름 (NaverWebtoon, Steam, etc.)
    private String thumbnailUrl;        // 썸네일 이미지 URL
    private List<String> watchProviders; // OTT 플랫폼 목록 (Netflix, Disney Plus 등)
    
    // ===== 트렌드 (2026-10-04) =====
    private String portraitImageUrl;    // 게임 세로 표지 (Content.portraitImageUrl, 없으면 null)
    private Double ratingScore;         // 순위를 받을 때의 평가 — 게임: 긍정 비율(0~1) · 영화/시리즈: TMDB 평점
    private Integer ratingCount;        // 게임: 리뷰 수 · 영화/시리즈: 투표 수
    private String ratingLabel;         // 게임: Steam 판정(영문)
    private Integer previousRanking;    // 기준일 순위 — 기준일 기록에 없으면 null(= NEW, rankBaseDate 가 있을 때만)
    private String rankBaseDate;        // 비교 기준일(yyyy-MM-dd) — null 이면 비교할 기록이 없다(변동 칸을 비운다)

    // Content 상세 정보 (매핑된 경우만, 선택적)
    private ContentInfo content;
    
    /**
     * 내부 작품 상세 정보
     * - content가 null이면 아직 우리 DB에 수집되지 않은 신작
     * - content가 있으면 상세 페이지 이동 가능
     */
    @Getter
    @Setter
    public static class ContentInfo {
        private Long contentId;         // 내부 작품 ID
        private String domain;          // 도메인 (WEBTOON, GAME, AV, etc.)
        private String masterTitle;     // 마스터 제목
        private String posterImageUrl;  // 포스터 이미지 URL
    }
}


