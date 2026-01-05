package com.example.AOD.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkResponseDTO {
    private Long id;
    private String domain; // AV, GAME, WEBTOON, WEBNOVEL
    private String title;
    private String originalTitle;
    private String releaseDate; // ISO 8601 format: yyyy-MM-dd
    private String thumbnail;
    private String synopsis;
    private Double score;
    
    // 도메인별 동적 정보 (장르, 개발사, 작가 등)
    private Map<String, Object> domainInfo;
    
    // 플랫폼별 정보 (Steam, Netflix 등)
    private Map<String, Map<String, Object>> platformInfo;
}


