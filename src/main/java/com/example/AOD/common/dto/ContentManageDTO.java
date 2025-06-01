package com.example.AOD.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentManageDTO {
    private Long id;
    private String contentType;
    private String title;
    private String thumbnailUrl;
    private TypeSpecificData specificData;
    private Map<String, Long> platformIds;  // 플랫폼별 ID: {"netflix": 12345, "watcha": null}
}