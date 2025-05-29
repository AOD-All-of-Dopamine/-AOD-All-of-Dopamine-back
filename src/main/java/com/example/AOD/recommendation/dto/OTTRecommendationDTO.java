package com.example.AOD.recommendation.dto;

import java.util.List;

// OTT 추천용 DTO
public class OTTRecommendationDTO {
    private Long id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String releaseDate;
    private List<String> genres;
    private List<String> features;
    private List<String> actors;

    // 생성자
    public OTTRecommendationDTO() {}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }

    public List<String> getActors() { return actors; }
    public void setActors(List<String> actors) { this.actors = actors; }
}
