package com.example.shared.entity;




import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 200)
    private String author;
    @Column(length = 50)
    private String status;      // 연재중/완결
    private String weekday;     // 연재 요일 (mon, tue, wed 등), 완결작은 null
    private String ageRating;   // 연령등급 (전체이용가, 15세이용가 등)

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
