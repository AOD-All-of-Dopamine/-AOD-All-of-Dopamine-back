package com.example.AOD.naverWebtoonCrawler.domain;

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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import com.example.AOD.naverWebtoonCrawler.domain.dto.NaverWebtoonDTO;


@Entity
@Getter
@Setter
public class Webtoon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String publishDate;

    @Column(nullable = false,length = 500)
    private String summary;

    @Column(nullable = false)
    private String thumbnail;

    @ElementCollection
    @Enumerated(EnumType.STRING)
    private List<Days> uploadDays;

    @ManyToMany
//    @JoinTable(name = "webtoonAuthor")
    @JoinTable(
            name = "webtoon_author_mapping",
            joinColumns = @JoinColumn(name = "webtoon_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private List<WebtoonAuthor> webtoonAuthors;

    @ManyToMany
//    @JoinTable(name = "webtoonGenre")
    @JoinTable(
            name = "webtoon_genre_mapping",
            joinColumns = @JoinColumn(name = "webtoon_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<WebtoonGenre> webtoonGenres;

    public Webtoon() {
    }

    public Webtoon(NaverWebtoonDTO naverWebtoonDTO) {
        this.title = naverWebtoonDTO.getTitle();
        this.url = naverWebtoonDTO.getUrl();
        this.publishDate = naverWebtoonDTO.getPublishDate();
        this.summary = naverWebtoonDTO.getSummary();
        this.thumbnail = naverWebtoonDTO.getThumbnail();
        this.uploadDays = new ArrayList<>(naverWebtoonDTO.getUploadDays());

        // authors 문자열 리스트 → WebtoonAuthor 리스트로 변환
        this.webtoonAuthors = new ArrayList<>();
        for (String authorName : naverWebtoonDTO.getAuthors()) {
            this.webtoonAuthors.add(new WebtoonAuthor(authorName));
        }

        // genres 문자열 리스트 → WebtoonGenre 리스트로 변환
        this.webtoonGenres = new ArrayList<>();
        for (String genreName : naverWebtoonDTO.getGenres()) {
            this.webtoonGenres.add(new WebtoonGenre(genreName));
        }
    }
}
