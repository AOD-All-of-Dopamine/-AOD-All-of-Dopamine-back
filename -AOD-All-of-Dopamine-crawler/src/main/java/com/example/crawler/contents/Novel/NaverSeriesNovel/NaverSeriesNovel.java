package com.example.crawler.contents.Novel.NaverSeriesNovel;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@Table(name = "naver_series_novel")
public class NaverSeriesNovel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==== 기본 메타 ====
    @Column(nullable = false)
    private String title;         // ex) "나 혼자만 레벨업"
    private String author;        // "추공"
    private String translator;    // 없으면 null
    @Column(columnDefinition = "text")
    private String synopsis;

    private String imageUrl;      // 썸네일
    private String productUrl;    // 네이버 시리즈 상세 URL

    // ==== 서비스 메타 ====
    private String titleId;       // productNo / titleId 등
    private String weekday;       // 연재 요일(수/토/…)
    private Integer episodeCount; // 회차 수
    private String status;        // 연재중/완결 등
    private LocalDate startedAt;

    private String publisher;
    private String ageRating;

    // ==== 장르: 다대다 대신 JSONB ====
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> genres = new ArrayList<>();

    // ==== 감사 필드 ====
    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }
}


