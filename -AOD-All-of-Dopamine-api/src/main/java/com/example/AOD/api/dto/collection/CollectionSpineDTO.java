package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 목록 카드의 미니 책장용 책등 한 권 — 컬렉션에 담긴 작품의 제목·포스터만.
 * 도메인은 싣지 않는다(컬렉션은 단일 도메인이라 CollectionSummaryDTO.domain 과 같다).
 * 상세를 부르지 않고도 목록에서 책등을 그리게 하려는 것 — 상세 GET 은 조회수를 +1 한다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionSpineDTO {
    private Long contentId;
    private String title;
    private String posterUrl; // null 가능 (포스터 없는 작품은 단색 책등)
}
