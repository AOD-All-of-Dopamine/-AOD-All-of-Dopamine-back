package com.example.AOD.domain.entity;



import com.example.AOD.domain.Content;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;

@Entity @Table(name="webtoon_contents")
@Setter
@Getter
public class WebtoonContent {
    @Id
    private Long contentId;

    @OneToOne @MapsId
    @JoinColumn(name="content_id",
            foreignKey=@ForeignKey(name="fk_webtoon_content_content"))
    private Content content;

    public WebtoonContent() {}

    public WebtoonContent(Content content) {
        this.content = content;
        this.contentId = content.getContentId();
    }

    private String author;
    private String illustrator;
    private String status;      // 연재중/완결
    private LocalDate startedAt;

    @Type(JsonType.class)
    @Column(columnDefinition="jsonb")
    private Map<String,Object> genres;

    // getters/setters...
}
