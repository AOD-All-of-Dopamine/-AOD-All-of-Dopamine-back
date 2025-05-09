package com.example.AOD.common.commonDomain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "game_common")
@Getter
@Setter
@NoArgsConstructor
public class GameCommon {
    @Id
    private Long id;

    @Version
    private Long version;

    private String title;
    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "game_common_genre", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "genre")
    private List<String> genre;

    private Long requiredAge;

    @Column(length = 10000)
    private String summary;

    private int initialPrice;
    private int finalPrice;
    private String platform;

    @ElementCollection
    @CollectionTable(name = "game_common_publisher", joinColumns = @JoinColumn(name = "game_id"))
    @Column(name = "publisher")
    private List<String> publisher;
}