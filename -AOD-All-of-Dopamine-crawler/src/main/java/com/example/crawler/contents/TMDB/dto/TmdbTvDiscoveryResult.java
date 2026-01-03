package com.example.crawler.contents.TMDB.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class TmdbTvDiscoveryResult {

    @JsonProperty("page")
    private int page;

    @JsonProperty("results")
    private List<TmdbTvShow> results; // TmdbMovie -> TmdbTvShow로 변경

    @JsonProperty("total_pages")
    private int totalPages;

    @JsonProperty("total_results")
    private int totalResults;
}

