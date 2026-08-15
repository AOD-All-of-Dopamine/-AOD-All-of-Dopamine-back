package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 컬렉션 목록 카드 (발견 페이지 / 내 컬렉션 공용) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionSummaryDTO {
    private Long id;
    private String title;
    private String description;
    private String domain;          // MOVIE/TV/GAME/WEBTOON/WEBNOVEL
    private String tint;            // PINE/OLIVE/SLATE/TERRACOTTA/MOCHA/PLUM
    private String visibility;      // PUBLIC/PRIVATE (내 컬렉션 배지용)
    private int likeCount;
    private long viewCount;
    private long itemCount;
    private String curatorNickname;
    private List<String> coverPosters; // 상위 3개 포스터 URL (조회 시 파생, 저장 안 함)
    private boolean likedByMe;         // 비로그인 시 false
    private LocalDateTime createdAt;
}
