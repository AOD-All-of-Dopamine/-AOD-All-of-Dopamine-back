package com.example.AOD.Novel.NaverSeriesNovel.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class NaverSeriesNovelDTO {
    private String title;
    private String author;
    private String translator;
    private String synopsis;

    private String imageUrl;
    private String productUrl;

    private String titleId;
    private String weekday;
    private Integer episodeCount;
    private String status;
    private LocalDate startedAt;

    private String publisher;
    private String ageRating;

    private List<String> genres = new ArrayList<>();
}
