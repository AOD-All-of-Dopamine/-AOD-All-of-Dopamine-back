package com.example.AOD.game.StreamAPI.domain;

import com.example.AOD.OTT.Netflix.domain.Genre;
import com.example.AOD.game.StreamAPI.dto.GameDetailDto.GameDetailDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter @Setter
@NoArgsConstructor
@ToString
public class SteamGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long steam_appid;

    @Column(nullable = false)
    private Long required_age;

    @Column(length = 10000)
    private String detailed_description;

    @Column(length = 10000)
    private String about_the_game;

    @Column(length = 10000)
    private String short_description;

    private String supported_languages;

    private String header_image;

    private String capsule_image;

    private String capsule_imagev5;

    private String website;

    @ManyToMany
    @JoinTable(
            name = "game_developer_mapping",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "developer_id")
    )
    private List<GameDeveloper> developers;


    @ManyToMany
    @JoinTable(
            name = "game_publisher_mapping",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "developer_id")
    )
    private List<GamePublisher> publishers;

    private int initialPrice;
    private int finalPrice;

    @ManyToMany
    @JoinTable(
            name = "game_category_mapping",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<SteamGameCategory> categories;

    @ManyToMany
    @JoinTable(
            name = "game_genre_mapping",
            joinColumns = @JoinColumn(name = "game_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private List<GameGenre> genres;

    public SteamGame(GameDetailDto dto) {
        this.name = dto.getName();
        this.steam_appid = dto.getSteam_appid();
        this.required_age = dto.getRequired_age();
        this.detailed_description = dto.getDetailed_description();
        this.about_the_game = dto.getAbout_the_game();
        this.short_description = dto.getShort_description();
        this.supported_languages = dto.getSupported_languages();
        this.header_image = dto.getHeader_image();
        this.capsule_image = dto.getCapsule_image();
        this.capsule_imagev5 = dto.getCapsule_imagev5();
        this.website = dto.getWebsite();

        if(dto.getPrice_overview() != null) {
            this.initialPrice = dto.getPrice_overview().getInitial();
            this.finalPrice = dto.getPrice_overview().getFinal_();
        } else{
            this.initialPrice = 0;
            this.finalPrice = 0;
        }

        this.developers = dto.getDevelopers().stream()
                .map(GameDeveloper::new).collect(Collectors.toList());
        this.publishers = dto.getPublishers().stream()
                .map(GamePublisher::new).collect(Collectors.toList());
        this.categories = dto.getCategories().stream()
                .map(category -> new SteamGameCategory(category.getDescription()))
                .collect(Collectors.toList());
        this.genres = dto.getGenres().stream()
                .map(genre -> new GameGenre(genre.getName()))
                .collect(Collectors.toList());
    }
}
