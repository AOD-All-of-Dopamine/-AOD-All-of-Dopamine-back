package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "ott_common")
@Getter
@Setter
@NoArgsConstructor
public class OTTCommon {
    @Id
    private Long id;

    @Version
    private Long version;

    private String title;
    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "ott_common_genre", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "genre")
    private List<String> genre;

    private String type;
    private String thumbnail;

    @Column(length = 1000)
    private String description;

    private String creator;
    private String maturityRating;
    private int releaseYear;

    @ElementCollection
    @CollectionTable(name = "ott_common_actors", joinColumns = @JoinColumn(name = "ott_id"))
    @Column(name = "actor")
    private List<String> actors;
}