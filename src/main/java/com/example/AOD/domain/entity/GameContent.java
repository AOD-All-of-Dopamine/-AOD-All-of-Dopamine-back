package com.example.AOD.domain.entity;


import com.example.AOD.domain.Content;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;

@Entity @Table(name="game_contents")
@Getter
@Setter
public class GameContent {
    @Id
    private Long contentId;

    @OneToOne @MapsId
    @JoinColumn(name="content_id",
            foreignKey=@ForeignKey(name="fk_game_content_content"))
    private Content content;

    private String developer;
    private String publisher;
    private LocalDate releaseDate;

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private Map<String,Object> platforms; // {windows:true, mac:false, ...}

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private Map<String,Object> genres;    // 자유 형식

    // getters/setters...
}
