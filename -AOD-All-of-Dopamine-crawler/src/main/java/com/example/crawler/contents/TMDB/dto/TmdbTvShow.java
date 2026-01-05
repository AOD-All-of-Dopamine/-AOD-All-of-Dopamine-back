// src/main/java/com/example/AOD/TMDB/dto/TmdbTvShow.java
package com.example.crawler.contents.TMDB.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class TmdbTvShow {

    @JsonProperty("id")
    private int id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("original_name")
    private String originalName;

    @JsonProperty("overview")
    private String overview;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("first_air_date")
    private String firstAirDate;

    // --- [ 수정된 필드 ] ---
    @JsonProperty("genres") // genre_ids -> genres (상세 API 응답)
    private List<Map<String, Object>> genres;

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

    // --- [ 추가된 필드 ] ---
    @JsonProperty("number_of_seasons")
    private Integer seasonCount;

    @JsonProperty("credits")
    private Map<String, List<Map<String, Object>>> credits;
}

