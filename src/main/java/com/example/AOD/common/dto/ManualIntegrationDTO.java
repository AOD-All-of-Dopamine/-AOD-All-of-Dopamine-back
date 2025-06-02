package com.example.AOD.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualIntegrationDTO {
    private Long configId;
    private String contentType;
    private List<Long> sourceIds;

    // 수동으로 입력/선택된 Common 엔티티 필드들
    private String title;
    private String imageUrl;
    private List<String> genre;

    // Novel 전용 필드
    private List<String> authors;
    private String status;
    private String publisher;
    private String ageRating;

    // Movie 전용 필드
    private String director;
    private List<String> actors;
    private String releaseDate;
    private Integer runningTime;
    private String country;
    private String movieAgeRating;
    private Integer totalAudience;
    private String summary;
    private Double rating;
    private Double reservationRate;
    private Boolean isRerelease;

    // OTT 전용 필드
    private String type;
    private String creator;
    private String description;
    private String maturityRating;
    private Integer releaseYear;
    private List<String> features;

    // Webtoon 전용 필드
    private String publishDate;
    private List<String> uploadDays;
    private String webtoonSummary;

    // Game 전용 필드
    private List<String> developers;
    private List<String> publishers;
    private Long requiredAge;
    private String gameSummary;
    private Integer initialPrice;
    private Integer finalPrice;

    // 각 필드에 대한 소스 선택 정보 (어떤 플랫폼에서 가져올지)
    private Map<String, String> fieldSources; // fieldName -> platformId

    // 사용자 정의 값 여부
    private Map<String, Boolean> customValues; // fieldName -> isCustom
}