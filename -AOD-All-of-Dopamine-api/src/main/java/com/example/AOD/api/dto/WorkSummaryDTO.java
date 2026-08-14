package com.example.AOD.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkSummaryDTO {
    private Long id;
    private String domain;
    private String title;
    private String thumbnail;
    private Double score;
    private Integer rank; // for ranking pages
    private String rankChange; // "up", "down", "new", or number
    private String releaseDate; // for new releases

    // ===== 카드 표시용 확장 (2026-08) — 목록 응답에서 배치 조회로 채움, 없으면 null =====
    private java.util.List<String> genres;      // contents.genres (마스터)
    private java.util.List<String> platforms;   // contents.platforms (수집 소스 + OTT)
    private String creator;                     // 도메인별 대표 제작자: 감독/개발사/작가
    private String weekday;                     // 웹툰 연재 요일 (mon~sun)
    private String status;                      // 웹툰 연재 상태 (연재중/완결)
    private String ageRating;                   // 웹툰/웹소설 연령 등급
    private String steamReviewDesc;             // 게임: Steam review_score_desc (영문 원문)
    private Integer steamPositivePct;           // 게임: 긍정 비율 % (total 기준)
    private Double externalRating;              // 영화/TV: TMDB vote_average (수집분부터)
}


