package com.example.AOD.Novel.NaverSeriesNovel.domain;

import com.example.AOD.Novel.NaverSeriesNovel.dto.NaverSeriesNovelDTO;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 1000) // URL은 길 수 있으므로 충분한 길이 설정
    private String imageUrl;

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

    @ManyToMany
    @JoinTable(
            name = "novel_author_mapping",
            joinColumns = @JoinColumn(name = "novel_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id")
    )
    private List<NaverSeriesNovelAuthor> authors = new ArrayList<>();
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
        this.imageUrl = naverSeriesNovelDTO.getImageUrl();

        // 작가와 장르는 외부에서 설정

        this.genres = new ArrayList<>();
        for(String genre : naverSeriesNovelDTO.getGenres()) {
            this.genres.add(new NaverSeriesNovelGenre(genre));
        }
    }

}
