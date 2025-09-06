package com.example.AOD.domain.entity;

import com.example.AOD.domain.Content;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;

@Entity @Table(name="webnovel_contents")
@Getter
@Setter
public class WebnovelContent {
    @Id
    private Long contentId;

    @OneToOne @MapsId
    @JoinColumn(name="content_id",
            foreignKey=@ForeignKey(name="fk_webnovel_content_content"))
    private Content content;

    private String author;
    private String translator;
    private LocalDate startedAt;

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private Map<String,Object> genres;

    // getters/setters...
}

