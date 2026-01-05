package com.example.crawler.contents.Novel.KakaoPageNovel;

import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class KakaoPageNovelDTO {
    // 공통 메타(마스터 후보)
    private String title;           // 작품명
    private String author;          // 작가
    private String synopsis;        // 소개/시놉시스
    private String imageUrl;        // 썸네일
    private String productUrl;      // 상세 URL
    private String seriesId;        // Kakao 페이지의 시리즈 식별자 (titleId 역할)
    private String status;          // "연재중"/"완결"
    private String publisher;       // 있을 경우
    private String ageRating;       // 있을 경우
    private List<String> genres = new ArrayList<>();
    private List<String> keywords = new ArrayList<>();

    // 플랫폼 고유 지표(PlatformData.attributes 로 보낼 값)
    private BigDecimal rating;      // 별점 (예: 9.7)
    private Long viewCount;         // 조회/뷰 수 (예: 3.4억 → 340000000)
    private Long commentCount;      // 댓글 수
}


