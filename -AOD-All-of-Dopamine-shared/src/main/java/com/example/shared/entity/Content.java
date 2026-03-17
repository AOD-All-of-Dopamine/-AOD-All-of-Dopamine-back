package com.example.shared.entity;


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

    @Column(nullable = false, length = 500)
    private String masterTitle;

    @Column(length = 500)
    private String originalTitle;
    private LocalDate releaseDate;
    @Column(length = 1000)
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
    // [✨ 최적화: 별점 역정규화(Denormalization) 필드]
    // 조회 시 발생하는 N+1 쿼리 문제를 해결하기 위해 리뷰의 평균과 총개수를 원장 테이블(Content)에 캐싱합니다.
    @Column(nullable = false, columnDefinition = "double precision default 0.0")
    private Double averageScore = 0.0;

    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer reviewCount = 0;

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    // getters/setters ...
}
