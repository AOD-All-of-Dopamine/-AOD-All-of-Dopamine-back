package com.example.AOD.recommendation.dto;

import java.util.List;

// 게임 추천용 DTO
public class GameRecommendationDTO {
    private Long id;
    private String title;
    private String description;
    private String thumbnailUrl;
    private String releaseDate;
    private List<String> genres;
    private List<String> categories;
    private List<String> developers;
    private List<String> publishers;

    // 생성자
    public GameRecommendationDTO() {}

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

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public List<String> getDevelopers() { return developers; }
    public void setDevelopers(List<String> developers) { this.developers = developers; }

    public List<String> getPublishers() { return publishers; }
    public void setPublishers(List<String> publishers) { this.publishers = publishers; }
}