package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** PATCH 부분 수정 — null 필드는 미변경 (domain은 아이템 정합성 때문에 변경 불가) */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionUpdateRequest {
    private String title;
    private String description;
    private String tint;
    private String visibility;
}
