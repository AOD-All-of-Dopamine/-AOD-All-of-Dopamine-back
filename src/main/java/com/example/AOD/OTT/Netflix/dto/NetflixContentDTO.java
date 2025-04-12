package com.example.AOD.OTT.Netflix.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NetflixContentDTO {
    private String id;
    private String title;
    private String type;
    private String url;
    private String detailUrl;
    private String thumbnail;
    private String description;
    private String maturityRating;
    private String releaseYear;
    private LocalDateTime crawledAt;
    private List<String> genres;
    private List<String> actors;
    private String creator;
}
