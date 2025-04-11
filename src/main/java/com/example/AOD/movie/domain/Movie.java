package com.example.AOD.movie.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    private String title;
    private String director;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_actors", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name ="actor")
    private List<String> actors;

    private double reservationRate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "genre")
    private List<String> genres;

    private Double rating;
    private Integer runningTime; // 분 단위

    private String country;

    private LocalDate releaseDate;

    // 재개봉 여부 필드 추가
    private Boolean isRerelease;

    private String ageRating; // 추가된 관람 연령대 필드

    @Column(unique = true)
    private String externalId; // 크롤링 소스에서의 고유 ID

    private LocalDate lastUpdated;

}