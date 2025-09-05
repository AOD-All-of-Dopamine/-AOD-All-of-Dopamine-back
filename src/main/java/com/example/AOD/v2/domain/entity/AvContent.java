package com.example.AOD.v2.domain.entity;

import com.example.AOD.v2.domain.Content;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Map;
import com.vladmihalcea.hibernate.type.json.JsonType;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "av_contents")
public class AvContent {

    @Id
    private Long contentId; // contents PK와 동일

    @OneToOne
    @MapsId
    @JoinColumn(name = "content_id",
            foreignKey = @ForeignKey(name = "fk_av_content_content"))
    private Content content;

    private Integer tmdbId;

    @Column(length = 16)
    private String avType; // MOVIE/TV/ANIME

    private LocalDate releaseDate;
    private Integer runtimeMin;
    private Integer seasonCount;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> cast;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> crew;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> genres;

    // getters/setters ...
}