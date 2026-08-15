package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 아이템 부분 수정 — null 필드는 미변경, comment 빈 문자열은 코멘트 삭제 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionItemUpdateRequest {
    private String comment;
    private Integer position;
}
