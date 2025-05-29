package com.example.AOD.commonV2.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MovieTypeData extends TypeSpecificData {
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
}