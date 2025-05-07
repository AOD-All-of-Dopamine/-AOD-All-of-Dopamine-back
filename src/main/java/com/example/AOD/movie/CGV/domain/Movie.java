package com.example.AOD.movie.CGV.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "movies")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String director;

    private double reservationRate;

    private Double rating;

    private Integer runningTime; // 분 단위

    private String country;

    private LocalDate releaseDate;

    // 재개봉 여부 필드
    private Boolean isRerelease;

    private String ageRating;

    // 썸네일 이미지 URL 필드 추가
    @Column(length = 1000)
    private String thumbnailUrl;

    @Column(unique = true)
    private String externalId; // 크롤링 소스에서의 고유 ID

    private LocalDate lastUpdated;

    // 배우와의 다대다 관계
    @ManyToMany
    @JoinTable(
            name = "movie_actor_mapping",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "actor_id")
    )
    private List<MovieActor> actors = new ArrayList<>();

    // 장르와의 다대다 관계
    @ManyToMany
    @JoinTable(
            name = "movie_genre_mapping",
            joinColumns = @JoinColumn(name = "movie_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<MovieGenre> genres = new ArrayList<>();
}