package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 담기 팝오버용 — 내 컬렉션 + 해당 작품 포함 여부 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyCollectionSummaryDTO {
    private Long id;
    private String title;
    private String domain;
    private String tint;
    private String visibility;
    private long itemCount;
    private boolean containsContent; // 조회한 contentId가 이미 담겨 있는지
}
