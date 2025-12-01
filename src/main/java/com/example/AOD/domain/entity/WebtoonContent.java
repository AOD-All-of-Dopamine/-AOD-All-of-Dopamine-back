package com.example.AOD.domain.entity;



import com.example.AOD.domain.Content;
import jakarta.persistence.*;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;
import org.springframework.data.domain.Persistable;

@Entity @Table(name="webtoon_contents")
@Setter
@Getter
public class WebtoonContent implements Persistable<Long> {
    @Id
    private Long contentId;

    @OneToOne @MapsId
    @JoinColumn(name="content_id",
            foreignKey=@ForeignKey(name="fk_webtoon_content_content"))
    private Content content;

    @Transient
    private boolean isNew = true;

    public WebtoonContent() {}

    public WebtoonContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }

    private String author;
    private String illustrator;
    private String status;      // 연재중/완결
    private String weekday;     // 연재 요일 (mon, tue, wed 등), 완결작은 null

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private List<String> genres;

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
