package com.example.AOD.recommendation.dto;

import java.util.List;

// 영화 추천용 DTO
public class MovieRecommendationDTO {
    private Long id;
    private String title;
    private String director;
    private String thumbnailUrl;
    private Integer runningTime;
    private String rating;
    private String ageRating;
    private String releaseDate;
    private List<String> genres;
    private List<String> actors;

    // 생성자
    public MovieRecommendationDTO() {}

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDirector() { return director; }
    public void setDirector(String director) { this.director = director; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public Integer getRunningTime() { return runningTime; }
    public void setRunningTime(Integer runningTime) { this.runningTime = runningTime; }

    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }

    public String getAgeRating() { return ageRating; }
    public void setAgeRating(String ageRating) { this.ageRating = ageRating; }

    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public List<String> getActors() { return actors; }
    public void setActors(List<String> actors) { this.actors = actors; }
}
