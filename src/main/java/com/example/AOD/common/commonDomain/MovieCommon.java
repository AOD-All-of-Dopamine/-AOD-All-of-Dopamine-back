package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "movie_common")
@Getter
@Setter
@NoArgsConstructor
public class MovieCommon {
    @Id
    private Long id;

    // 낙관적 잠금을 위한 버전 필드
    @Version
    private Long version;

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
    private String summary;
}