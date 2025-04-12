package com.example.AOD.OTT.Netflix.domain;


import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "netflix_content")
public class NetflixContent {

    @Id
    @Column(name = "content_id", length = 255, nullable = false)
    private String contentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 50)
    private String type;

    @Column(name = "main_url", length = 500)
    private String url;

    @Column(name = "detail_url", length = 500)
    private String detailUrl;

    @Column(columnDefinition = "TEXT")
    private String thumbnail;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String creator;

    @Column(name = "maturity_rating", length = 20)
    private String maturityRating;

    @Column(name = "release_year", length = 4)
    private String releaseYear;

    private LocalDateTime crawledAt;

    // 배우 리스트 (ManyToMany)
    @ManyToMany
    @JoinTable(
            name = "netflix_content_actor",
            joinColumns = @JoinColumn(name = "content_id"),
            inverseJoinColumns = @JoinColumn(name = "actor_id")
    )
    private List<Actor> actors;

    // 장르 리스트 (ManyToMany)
    @ManyToMany
    @JoinTable(
            name = "netflix_content_genre",
            joinColumns = @JoinColumn(name = "content_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<Genre> genres;

    // 특징 리스트 (ManyToMany)
    @ManyToMany
    @JoinTable(
            name = "netflix_content_feature",
            joinColumns = @JoinColumn(name = "content_id"),
            inverseJoinColumns = @JoinColumn(name = "feature_id")
    )
    private List<Feature> features;
}