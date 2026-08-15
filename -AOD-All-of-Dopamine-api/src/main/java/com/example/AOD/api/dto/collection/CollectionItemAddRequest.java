package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionItemAddRequest {
    private Long contentId;  // 필수
    private String comment;  // 선택, 200자 이내
}
