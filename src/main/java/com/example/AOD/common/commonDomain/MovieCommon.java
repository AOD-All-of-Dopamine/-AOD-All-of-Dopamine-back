package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "movie_common")
@Getter
@Setter
@NoArgsConstructor
public class MovieCommon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private String title;

    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "movie_common_genre", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "genre")
    private List<String> genre;

    private String releaseDate;
    private int runningTime;
    private String director;

    @ElementCollection
    @CollectionTable(name = "movie_common_actors", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "actor")
    private List<String> actors;

    private String ageRating;
    private int totalAudience;

    @Column(length = 1000)
    private String summary;

    // 추가 필드들
    private Double rating;
    private Double reservationRate;
    private String country;
    private Boolean isRerelease;

    // 1:1 관계 설정 - PlatformMapping 추가
    @OneToOne(mappedBy = "movieCommon", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private MoviePlatformMapping platformMapping;

    // 메타 정보 추가
    private LocalDateTime createdAt;
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

    // 플랫폼 매핑 설정 헬퍼 메서드
    public void setPlatformMapping(MoviePlatformMapping mapping) {
        this.platformMapping = mapping;
        if (mapping != null) {
            mapping.setMovieCommon(this);
        }
    }
}