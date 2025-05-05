package com.example.AOD.common.commonDomain;

import com.example.AOD.game.StreamAPI.domain.SteamGame;
import com.example.AOD.game.StreamAPI.domain.GameGenre;
import com.example.AOD.game.StreamAPI.domain.GamePublisher;
import com.example.AOD.game.StreamAPI.dto.GameDetailDto.GameDetailDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class GameCommon{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String imageUrl;

    @ManyToMany
    @JoinTable(
            name = "game_genre_mapping",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<GameGenre> genre;


    private Long requiredAge;

    @Column(length = 10000)
    private String summary;

    private int initialPrice;

    private int finalPrice;

    private String platform;

    @ManyToMany
    @JoinTable(
            name = "game_publisher_mapping",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "developer_id")
    )
    private List<GamePublisher> publisher;

    public GameCommon(SteamGame steamGame) {
        this.id = steamGame.getId();
        this.title = steamGame.getName();
        this.imageUrl = steamGame.getCapsule_imagev5();
        this.genre = steamGame.getGenres();
        this.requiredAge = steamGame.getRequired_age();
        this.summary = steamGame.getDetailed_description();
        this.initialPrice = steamGame.getInitialPrice();
        this.finalPrice = steamGame.getFinalPrice();
        this.platform = "Steam";
        this.publisher = steamGame.getPublishers();
    }

}
