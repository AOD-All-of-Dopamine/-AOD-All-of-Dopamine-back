package com.example.crawler.contents.Novel.NaverSeriesNovel;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NaverSeriesNovelDTO {
    private String title;           // "화산귀환"
    private String author;          // "비가"
    private String publisher;       // "러프미디어"
    private String status;          // "연재중"/"완결"
    private String ageRating;       // "전체 이용가"
    private String synopsis;        // 소개
    private String imageUrl;        // 썸네일
    private String productUrl;      // 상세 URL
    private String titleId;         // productNo
    private List<String> genres;    // ["무협", ...]

    private BigDecimal rating;      // 별점
    private Long downloadCount;     // 관심 수(다운로드로 사용)
    private Long commentCount;      // 댓글 수
}


