package com.example.AOD.domain.entity;

import com.example.AOD.domain.Content;
import jakarta.persistence.*;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;
import org.springframework.data.domain.Persistable;

@Entity @Table(name="webnovel_contents")
@Getter
@Setter
public class WebnovelContent implements Persistable<Long> {
    @Id
    private Long contentId;

    @OneToOne @MapsId
    @JoinColumn(name="content_id",
            foreignKey=@ForeignKey(name="fk_webnovel_content_content"))
    private Content content;

    @Transient
    private boolean isNew = true;

    public WebnovelContent() {}

    public WebnovelContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }

    @Column(length = 200)
    private String author;
    @Column(length = 200)
    private String publisher;
    @Column(length = 50)
    private String ageRating;

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private List<String> genres;

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

