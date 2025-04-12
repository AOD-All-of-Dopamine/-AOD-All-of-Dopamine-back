package com.example.AOD.OTT.Netflix.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

@Data
@Entity
public class NetflixContent {

    @Id
    @Column(name = "content_id",nullable = false ,length = 255)
    private String contentId;

    @Column(nullable = false,length = 255)
    private String title;

    @Column(name = "synopsis", columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "thumbnail_url", nullable = false, columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "maturity_rating", length = 20)
    private String maturityRating;

    @Column(name = "release_year", length = 4)
    private String releaseYear;
}
