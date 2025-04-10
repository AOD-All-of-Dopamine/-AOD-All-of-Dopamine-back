package com.example.AOD.StreamAPI.dto.GameDetailDto;

import java.util.List;
import lombok.Getter;

@Getter
public class GameDetailDto {
    private String type; //type = "game"이 아니라면 바인딩 제대로 안됨
    private String name;
    private Long steam_appid;
    private Long required_age;
    private String detailed_description;
    private String about_the_game;
    private String short_description;
    private String supported_languages;
    private String header_image;
    private String capsule_image;
    private String capsule_imagev5;
    private String website;
    private List<String> developers;
    private List<String> publishers;
    private PriceOverview price_overview; //무료 게임의 경우 null
    private Platforms platforms;
    private List<Category> categories;
    private List<Genre> genres;
    private List<Screenshot> screenshots;
    private ReleaseDate release_date;
}
