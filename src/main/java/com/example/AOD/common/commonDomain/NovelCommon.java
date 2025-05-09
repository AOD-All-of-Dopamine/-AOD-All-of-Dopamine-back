package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "novel_common")
@Getter
@Setter
@NoArgsConstructor
public class NovelCommon {
    @Id
    private Long id;

    private String title;

    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "novel_common_genre", joinColumns = @JoinColumn(name = "novel_id"))
    @Column(name = "genre")
    private List<String> genre;

    private String status;
    private String author;
    private String ageRating;
}