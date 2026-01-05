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

    // platforms는 객체이므로 JSONB 유지
    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private Map<String,Object> platforms; // {windows:true, mac:false, ...}

    // 장르 목록 (PostgreSQL text[] 배열)
    @Column(name = "genres", columnDefinition = "text[]")
    private List<String> genres = new ArrayList<>();

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
