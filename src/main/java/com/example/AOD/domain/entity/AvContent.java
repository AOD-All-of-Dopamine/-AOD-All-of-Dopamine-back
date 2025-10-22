package com.example.AOD.domain.entity;

import com.example.AOD.domain.Content;
import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "av_contents")
@Getter
@Setter
public class AvContent implements Persistable<Long> {

    @Id
    private Long contentId; // contents PK와 동일

    @OneToOne
    @MapsId
    @JoinColumn(name = "content_id",
            foreignKey = @ForeignKey(name = "fk_av_content_content"))
    private Content content;

    @Transient
    private boolean isNew = true;

    public AvContent() {}

    public AvContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }

    private Integer tmdbId;

    @Column(length = 16)
    private String avType; // MOVIE 또는 TV

    private LocalDate releaseDate;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
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