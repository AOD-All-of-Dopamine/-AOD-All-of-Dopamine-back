package com.example.shared.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TMDB 영화 콘텐츠 엔티티
 * - 기존 AvContent에서 분리됨
 * - movie_contents 테이블과 매핑
 */
@Entity
@Table(name = "movie_contents")
@Getter
@Setter
public class MovieContent implements Persistable<Long> {

    @Id
    private Long contentId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "content_id",
            foreignKey = @ForeignKey(name = "fk_movie_content_content"))
    private Content content;

    @Transient
    private boolean isNew = true;

    public MovieContent() {}

    public MovieContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }

    // 상영 시간 (분)
    private Integer runtime;

    // genres는 contents(마스터)로 승격됨 (2026-07) — Content.genres 사용

    // platforms는 contents(마스터)로 승격됨 (2026-07) — Content.platforms 사용

    // 감독 목록 (PostgreSQL text[] 배열)
    @Column(name = "directors", columnDefinition = "text[]")
    private List<String> directors = new ArrayList<>();

    // 출연진 목록 (PostgreSQL text[] 배열)
    @Column(name = "cast_members", columnDefinition = "text[]")
    private List<String> cast = new ArrayList<>();

    @Override
    public Long getId() {
        return contentId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        this.isNew = false;
    }
}
