package com.example.AOD.domain.entity;


import com.example.AOD.domain.Content;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.List;
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

    @Column(length = 200)
    private String developer;
    @Column(length = 200)
    private String publisher;
    private LocalDate releaseDate;

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private Map<String,Object> platforms; // {windows:true, mac:false, ...}

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private List<String> genres;    // 자유 형식

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
