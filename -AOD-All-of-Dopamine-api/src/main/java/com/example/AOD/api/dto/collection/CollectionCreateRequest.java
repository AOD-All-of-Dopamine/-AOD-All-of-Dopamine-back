package com.example.AOD.api.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectionCreateRequest {
    private String title;       // 필수, 60자 이내
    private String description; // 선택, 300자 이내
    private String domain;      // 필수, Domain enum명
    private String tint;        // 선택, 기본 PINE
    private String visibility;  // 선택, 기본 PUBLIC
}
