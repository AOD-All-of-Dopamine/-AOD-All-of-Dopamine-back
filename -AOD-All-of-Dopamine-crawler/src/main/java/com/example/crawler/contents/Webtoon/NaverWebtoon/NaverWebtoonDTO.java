package com.example.crawler.contents.Webtoon.NaverWebtoon;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class NaverWebtoonDTO {

    // ==== 기본 정보 ====
    private String title;           // 웹툰 제목
    private String author;          // 작가명 (여러명일 경우 콤마 구분)
    private String synopsis;        // 줄거리/소개
    private String imageUrl;        // 썸네일 이미지 URL
    private String productUrl;      // 웹툰 상세 페이지 URL

    // ==== 웹툰 메타데이터 ====
    private String titleId;         // 네이버 내부 웹툰 ID (titleId)
    private String weekday;         // 연재 요일 (mon, tue, wed, thu, fri, sat, sun), 완결작은 null
    private String status;          // 연재상태 (연재중, 완결, 휴재 등)
    private Integer episodeCount;   // 총 에피소드 수
    private LocalDate releaseDate;  // 첫 화 연재 날짜 (1화 날짜)

    // ==== 서비스 정보 ====
    private String ageRating;       // 연령등급 (전체이용가, 15세이용가 등)
    private List<String> genres;    // 장르 목록 (페이지 태그 원천, 타 도메인 payload 키와 통일)

    // ==== 인기/평점 정보 ====
    private Long likeCount;         // 관심 수 (페이지의 '관심' 수치 — 좋아요 아님)

    // ==== 추가 메타 ====
    private String crawlSource;     // 크롤링 소스 (weekday_wed, finish 등)
}

