package com.example.crawler.contents.Webtoon.NaverWebtoon;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
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
    private List<String> tags;      // 태그 목록 (hashtag 형태)

    // ==== 인기/평점 정보 ====
    private Long likeCount;         // 좋아요 수

    // ==== 추가 정보 ====
    private String serviceType;     // 서비스 타입 (일반, 베스트도전, 도전만화 등)

    // ==== 추가 메타 ====
    private String originalPlatform; // 원본 플랫폼 (항상 "NAVER_WEBTOON")
    private String crawlSource;     // 크롤링 소스 (weekday_wed, finish 등)
}

