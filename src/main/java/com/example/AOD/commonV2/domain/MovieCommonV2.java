package com.example.AOD.commonV2.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "movie_common_v2")
@Getter
@Setter
@NoArgsConstructor
public class MovieCommonV2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 기본 정보
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String director;

    @ElementCollection
    @CollectionTable(name = "movie_common_v2_actors", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "actor")
    private List<String> actors;

    @ElementCollection
    @CollectionTable(name = "movie_common_v2_genres", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "genre")
    private List<String> genres;

    private Double rating;
    private Double reservationRate;
    private Integer runningTime;
    private String country;
    private LocalDate releaseDate;
    private Boolean isRerelease;
    private String ageRating;

    @Column(length = 1000)
    private String thumbnailUrl;

    // 1:1 관계 설정
    @OneToOne(mappedBy = "movieCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MoviePlatformMapping platformMapping;

    // 메타 정보
    private LocalDate createdAt;
    private LocalDate updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDate.now();
        updatedAt = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDate.now();
    }

    // 편의 메서드들
    public boolean isOnCgv() {
        return platformMapping != null && platformMapping.getCgvId() != null && platformMapping.getCgvId() > 0;
    }

    public boolean isOnMegabox() {
        return platformMapping != null && platformMapping.getMegaboxId() != null && platformMapping.getMegaboxId() > 0;
    }

    public boolean isOnLotteCinema() {
        return platformMapping != null && platformMapping.getLotteCinemaId() != null && platformMapping.getLotteCinemaId() > 0;
    }
}