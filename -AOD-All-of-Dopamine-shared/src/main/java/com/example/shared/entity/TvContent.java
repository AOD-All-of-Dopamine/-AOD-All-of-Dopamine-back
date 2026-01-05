package com.example.shared.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TMDB TV쇼 콘텐츠 엔티티
 * - 기존 AvContent에서 분리됨
 * - tv_contents 테이블과 매핑
 */
@Entity
@Table(name = "tv_contents")
@Getter
@Setter
public class TvContent implements Persistable<Long> {

    @Id
    private Long contentId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "content_id",
            foreignKey = @ForeignKey(name = "fk_tv_content_content"))
    private Content content;

    @Transient
    private boolean isNew = true;

    public TvContent() {}

    public TvContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }


    // 시즌 수
    private Integer seasonCount;

    // 에피소드 평균 러닝타임 (분)
    private Integer episodeRuntime;

    // 장르 목록 (PostgreSQL text[] 배열)
    @Column(name = "genres", columnDefinition = "text[]")
    private List<String> genres = new ArrayList<>();

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
