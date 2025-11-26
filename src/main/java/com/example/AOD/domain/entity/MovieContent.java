package com.example.AOD.domain.entity;

import com.example.AOD.domain.Content;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
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

    // 개봉일
    private LocalDate releaseDate;

    // 상영 시간 (분)
    private Integer runtime;

    // 장르 목록
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> genres;

    // 감독 목록
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> directors;

    // 출연진 목록 (상위 10명)
    @Type(JsonType.class)
    @Column(name = "cast_members", columnDefinition = "jsonb")
    private List<String> cast;

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
