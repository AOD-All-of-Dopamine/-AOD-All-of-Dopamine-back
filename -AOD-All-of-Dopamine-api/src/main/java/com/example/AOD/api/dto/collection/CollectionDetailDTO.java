package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 컬렉션 상세 (목록 카드 필드 + items) */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionDetailDTO {
    private Long id;
    private String title;
    private String description;
    private String domain;
    private String tint;
    private String visibility;
    private int likeCount;
    private long viewCount;         // 이번 조회(+1) 반영 값
    private long itemCount;
    private String curatorNickname;
    private List<String> coverPosters;
    private boolean likedByMe;
    private boolean owner;          // 요청자가 소유자인지 (편집 진입 판단용)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CollectionItemDTO> items;
}
