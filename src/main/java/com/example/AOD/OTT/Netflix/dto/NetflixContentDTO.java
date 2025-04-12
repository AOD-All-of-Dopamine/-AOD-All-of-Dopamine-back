package com.example.AOD.OTT.Netflix.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NetflixContentDTO {
    private String contentId;
    private String title;
    private String type;
    private String url;
    private String detailUrl;
    private String thumbnail;
    private String description;
    private String creator;
    private String maturityRating;
    private String releaseYear;
    private LocalDateTime crawledAt;
    private List<String> actors;
    private List<String> genres;
    private List<String> features;
}
