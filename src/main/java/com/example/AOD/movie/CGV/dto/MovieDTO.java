package com.example.AOD.movie.CGV.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieDTO {
    private String title;
    private String director;
    private List<String> actors;
    private List<String> genres;
    private Double rating;
    private Double reservationRate;
    private Integer runningTime;
    private String country;
    private LocalDate releaseDate;
    private Boolean isRerelease;
    private String ageRating;
    private String thumbnailUrl; // 썸네일 이미지 URL 필드 추가
    private String externalId;
    private LocalDate lastUpdated;
}
