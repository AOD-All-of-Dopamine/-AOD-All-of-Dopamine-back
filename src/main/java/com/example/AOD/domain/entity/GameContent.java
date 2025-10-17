package com.example.AOD.domain.entity;


import com.example.AOD.domain.Content;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;
import org.springframework.data.domain.Persistable;

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
