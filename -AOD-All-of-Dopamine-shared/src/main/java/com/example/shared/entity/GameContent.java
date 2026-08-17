package com.example.shared.entity;



import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity @Table(name="game_contents")
@Getter
@Setter
public class GameContent implements Persistable<Long> {
    @Id
    private Long contentId;

    @OneToOne @MapsId
    @JoinColumn(name="content_id",
            foreignKey=@ForeignKey(name="fk_game_content_content"))
    private Content content;

    @Transient
    private boolean isNew = true;

    public GameContent() {}

    public GameContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }

    @Column(length = 200)
    private String developer;
    @Column(length = 200)
    private String publisher;

    // Steam 리뷰 총수 (2026-08 필터 축 승격, V6 — 원천: attributes.review_summary.total_reviews).
    // null = 미수집 (재크롤 시 steam.yml 매핑으로 채워짐) — reviewCountMin 필터에서 자연 제외.
    @Column(name = "review_count")
    private Integer reviewCount;

    // OS 플랫폼 정보 (Windows, Mac 등) - JSONB 유지
    @Type(JsonType.class)
    @Column(name = "os_platforms", columnDefinition="jsonb")
    private Map<String,Object> osPlatforms; // {windows:true, mac:false, ...}

    // genres는 contents(마스터)로 승격됨 (2026-07) — Content.genres 사용

    // platforms는 contents(마스터)로 승격됨 (2026-07) — Content.platforms 사용

    // getters/setters...

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
