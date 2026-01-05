package com.example.AOD.user.dto;

import java.util.List;

public class SignUpRequest {
    private String username;
    private String email;
    private String password;

    // 초기 선호도 설정 필드 추가
    private List<String> preferredGenres;
    private List<String> preferredContentTypes;
    private Integer ageGroup;
    private String preferredAgeRating;
    private String favoriteDirectors;
    private String favoriteAuthors;
    private String favoriteActors;
    private Boolean likesNewContent;
    private Boolean likesClassicContent;
    private String additionalNotes;

    // 기존 Getters & Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // 새로운 선호도 Getters & Setters
    public List<String> getPreferredGenres() { return preferredGenres; }
    public void setPreferredGenres(List<String> preferredGenres) { this.preferredGenres = preferredGenres; }

    public List<String> getPreferredContentTypes() { return preferredContentTypes; }
    public void setPreferredContentTypes(List<String> preferredContentTypes) { this.preferredContentTypes = preferredContentTypes; }

    public Integer getAgeGroup() { return ageGroup; }
    public void setAgeGroup(Integer ageGroup) { this.ageGroup = ageGroup; }

    public String getPreferredAgeRating() { return preferredAgeRating; }
    public void setPreferredAgeRating(String preferredAgeRating) { this.preferredAgeRating = preferredAgeRating; }

    public String getFavoriteDirectors() { return favoriteDirectors; }
    public void setFavoriteDirectors(String favoriteDirectors) { this.favoriteDirectors = favoriteDirectors; }

    public String getFavoriteAuthors() { return favoriteAuthors; }
    public void setFavoriteAuthors(String favoriteAuthors) { this.favoriteAuthors = favoriteAuthors; }

    public String getFavoriteActors() { return favoriteActors; }
    public void setFavoriteActors(String favoriteActors) { this.favoriteActors = favoriteActors; }

    public Boolean getLikesNewContent() { return likesNewContent; }
    public void setLikesNewContent(Boolean likesNewContent) { this.likesNewContent = likesNewContent; }

    public Boolean getLikesClassicContent() { return likesClassicContent; }
    public void setLikesClassicContent(Boolean likesClassicContent) { this.likesClassicContent = likesClassicContent; }

    public String getAdditionalNotes() { return additionalNotes; }
    public void setAdditionalNotes(String additionalNotes) { this.additionalNotes = additionalNotes; }
}

