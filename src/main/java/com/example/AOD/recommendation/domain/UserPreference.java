// 1. 사용자 선호도 도메인 클래스
package com.example.AOD.recommendation.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor
public class UserPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @ElementCollection
    @CollectionTable(name = "user_preferred_genres", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "genre")
    private List<String> preferredGenres;

    @ElementCollection
    @CollectionTable(name = "user_preferred_content_types", joinColumns = @JoinColumn(name = "preference_id"))
    @Column(name = "content_type")
    private List<String> preferredContentTypes;

    private Integer ageGroup;
    private String preferredAgeRating;

    @Column(columnDefinition = "TEXT")
    private String favoriteDirectors;

    @Column(columnDefinition = "TEXT")
    private String favoriteAuthors;

    @Column(columnDefinition = "TEXT")
    private String favoriteActors;

    private Boolean likesNewContent;
    private Boolean likesClassicContent;

    @Column(columnDefinition = "TEXT")
    private String additionalNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}