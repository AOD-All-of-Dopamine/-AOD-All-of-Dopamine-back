package com.example.AOD.domain;

import com.example.AOD.domain.entity.Domain;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Setter
@Getter
@Table(name = "contents",
        indexes = @Index(name = "idx_contents_lookup", columnList = "domain,masterTitle,releaseDate"))
public class Content {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Domain domain;

    @Column(nullable = false)
    private String masterTitle;

    private String originalTitle;
    private LocalDate releaseDate;
    private String posterImageUrl;

    @Column(columnDefinition = "text")
    private String synopsis;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    // getters/setters ...
}