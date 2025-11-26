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

    // 첨 방영일
    private LocalDate firstAirDate;

    // 시즌 수
    private Integer seasonCount;

    // 에피소드 평균 러닝타임 (분)
    private Integer episodeRuntime;

    // 장르 목록
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> genres;

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
