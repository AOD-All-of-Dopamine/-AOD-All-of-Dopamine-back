package com.example.AOD.NaverSeriesNovel.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class NaverSeriesNovelDTO {
    private String title;
    private String url;
    private String status;
    private List<String> genres;
    private String author;
    private String publisher;
    private String ageRating;
}