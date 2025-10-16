package com.example.AOD.domain.entity;

import com.example.AOD.domain.Content;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "av_contents")
@Getter
@Setter
public class AvContent {

    @Id
    private Long contentId; // contents PK와 동일

    @OneToOne
    @MapsId
    @JoinColumn(name = "content_id",
            foreignKey = @ForeignKey(name = "fk_av_content_content"))
    private Content content;

    public AvContent() {}

    public AvContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }

    private Integer tmdbId;

    @Column(length = 16)
    private String avType; // MOVIE 또는 TV

    private LocalDate releaseDate;

    // [개선] runtimeMin, seasonCount, castMembers, crewMembers 필드 제거.
    // 이 정보들은 PlatformData의 attributes에 저장되므로 Entity에 중복으로 정의할 필요가 없습니다.

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> genres;

}