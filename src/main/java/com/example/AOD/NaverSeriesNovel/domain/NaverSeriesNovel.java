package com.example.AOD.NaverSeriesNovel.domain;

import com.example.AOD.NaverSeriesNovel.dto.NaverSeriesNovelDTO;
import com.example.AOD.NaverSeriesNovel.repository.NaverSeriesAuthorRepository;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Getter
@Setter
public class NaverSeriesNovel {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String title;

    // 웹소설 상세 페이지 URL
    @Column(nullable = false)
    private String url;

    //완결 유무
    @Column(nullable = false)
    private String status;

    //장르, 한개만들어가있음
    @ManyToMany
    @JoinTable(
            name = "novel_genre_mapping",
            joinColumns = @JoinColumn(name = "novel_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<NaverSeriesNovelGenre> genres;

    @ManyToOne
    @JoinColumn(name = "author_id", nullable = true)
    private NaverSeriesNovelAuthor author;
    //출판사
    @Column(nullable = false)
    private String publisher;

    @Column(nullable = false)
    private String ageRating;

    public NaverSeriesNovel() {

    }

    public NaverSeriesNovel(NaverSeriesNovelDTO naverSeriesNovelDTO) {
        this.title = naverSeriesNovelDTO.getTitle();
        this.url = naverSeriesNovelDTO.getUrl();
        this.status = naverSeriesNovelDTO.getStatus();
        this.publisher = naverSeriesNovelDTO.getPublisher();
        this.ageRating = naverSeriesNovelDTO.getAgeRating();

        //작가 추가해야함

        this.genres = new ArrayList<>();
        for(String genre : naverSeriesNovelDTO.getGenres()) {
            this.genres.add(new NaverSeriesNovelGenre(genre));
        }
    }

}
