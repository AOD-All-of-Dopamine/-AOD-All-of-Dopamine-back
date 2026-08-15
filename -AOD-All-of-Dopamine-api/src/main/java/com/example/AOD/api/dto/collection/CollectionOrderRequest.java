package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 일괄 재정렬 — 컬렉션의 전체 아이템 id를 원하는 순서대로 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionOrderRequest {
    private List<Long> itemIds;
}
