package com.example.AOD.common.commonDomain;

import com.example.AOD.Webtoon.NaverWebtoon.domain.Days;
import com.example.AOD.Webtoon.NaverWebtoon.domain.Webtoon;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonAuthor;
import com.example.AOD.Webtoon.NaverWebtoon.domain.WebtoonGenre;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
public class WebtoonCommon{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String imageUrl;

    @ManyToMany
    @JoinTable(
            name = "webtoon_genre_mapping",
            joinColumns = @JoinColumn(name = "webtoon_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<WebtoonGenre> genre;

    @Column(nullable = false)
    private String publishDate;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<Days> uploadDay;

    @ManyToMany
    @JoinTable(
            name = "webtoon_author_mapping",
            joinColumns = @JoinColumn(name = "webtoon_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private List<WebtoonAuthor> author;

    @Column(nullable = false,length = 500)
    private String summary;

    private String platform;

    public WebtoonCommon(Webtoon webtoon) {
        this.id = webtoon.getId();
        this.title = webtoon.getTitle();
        this.imageUrl = webtoon.getThumbnail();
        this.genre = webtoon.getWebtoonGenres();
        this.publishDate = webtoon.getPublishDate();
        this.uploadDay = webtoon.getUploadDays();
        this.author = webtoon.getWebtoonAuthors();
        this.summary = webtoon.getSummary();
        this.platform = "Naver";

    }
}
