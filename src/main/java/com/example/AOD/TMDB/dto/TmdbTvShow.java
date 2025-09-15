package com.example.AOD.TMDB.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TmdbTvShow {

    @JsonProperty("id")
    private int id;

    @JsonProperty("name")
    private String name; // 영화의 'title'에 해당

    @JsonProperty("original_name")
    private String originalName; // 영화의 'original_title'에 해당

    @JsonProperty("overview")
    private String overview;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("first_air_date")
    private String firstAirDate; // 영화의 'release_date'에 해당

    @JsonProperty("genre_ids")
    private List<Integer> genreIds;

    @JsonProperty("vote_average")
    private double voteAverage;

    @JsonProperty("vote_count")
    private int voteCount;

    @JsonProperty("popularity")
    private double popularity;

    @JsonProperty("original_language")
    private String originalLanguage;

    @JsonProperty("origin_country")
    private List<String> originCountry;
}