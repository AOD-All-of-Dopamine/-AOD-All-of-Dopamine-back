// src/main/java/com/example/AOD/TMDB/dto/TmdbMovie.java

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
public class TmdbMovie {

    @JsonProperty("id")
    private int id;

    @JsonProperty("title")
    private String title;

    @JsonProperty("original_title")
    private String originalTitle;

    @JsonProperty("overview")
    private String overview;

    @JsonProperty("poster_path")
    private String posterPath;

    @JsonProperty("backdrop_path")
    private String backdropPath;

    @JsonProperty("release_date")
    private String releaseDate;

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

    @JsonProperty("adult")
    private boolean adult;

    // --- [ 추가된 필드 ] ---
    @JsonProperty("runtime")
    private Integer runtime;

    @JsonProperty("credits")
    private Map<String, List<Map<String, Object>>> credits; // cast와 crew 포함
}

