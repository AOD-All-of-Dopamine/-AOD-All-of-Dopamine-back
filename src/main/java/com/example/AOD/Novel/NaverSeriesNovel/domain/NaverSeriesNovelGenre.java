package com.example.AOD.Novel.NaverSeriesNovel.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class NaverSeriesNovelGenre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    // 장르명
    @Column(nullable = false, unique = true)
    private String name;

    public NaverSeriesNovelGenre(String genre) {
        this.name = genre;
    }

}
