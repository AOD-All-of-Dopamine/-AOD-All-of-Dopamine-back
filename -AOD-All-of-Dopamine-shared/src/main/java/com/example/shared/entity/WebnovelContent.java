package com.example.shared.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.util.ArrayList;
import java.util.List;

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

    // 장르 목록 (PostgreSQL text[] 배열)
    @Column(name = "genres", columnDefinition = "text[]")
    private List<String> genres = new ArrayList<>();

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

