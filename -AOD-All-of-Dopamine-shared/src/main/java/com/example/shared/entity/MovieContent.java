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

    // 장르 목록 (PostgreSQL text[] 배열)
    @Column(name = "genres", columnDefinition = "text[]")
    private List<String> genres = new ArrayList<>();

    // 감독 목록 (PostgreSQL text[] 배열)
    @Column(name = "directors", columnDefinition = "text[]")
    private List<String> directors = new ArrayList<>();

    // 출연진 목록 (PostgreSQL text[] 배열)
    @Column(name = "cast_members", columnDefinition = "text[]")
    private List<String> cast = new ArrayList<>();

    // TODO: OTT 플랫폼 정보 추가 (예: ["Netflix", "Disney Plus", "Watcha"])
    // @Type(JsonType.class)
    // @Column(columnDefinition = "jsonb")
    // private List<String> watchProviders;

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
