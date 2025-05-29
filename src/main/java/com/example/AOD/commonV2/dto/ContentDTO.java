package com.example.AOD.commonV2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentDTO {
    private Long id;
    private String contentType;  // "movie", "game", "novel", "webtoon", "ott"
    private String title;
    private String thumbnailUrl;
    private LocalDate createdAt;
    private LocalDate updatedAt;
    private Map<String, Boolean> availablePlatforms;  // 각 플랫폼 존재 여부: {"netflix": true, "watcha": false}
}