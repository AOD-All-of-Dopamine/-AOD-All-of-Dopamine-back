package com.example.crawler.ranking.tmdb.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * TMDB 플랫폼 타입 (SOLID - OCP 준수)
 */
@Getter
@RequiredArgsConstructor
public enum TmdbPlatformType {
    MOVIE("TMDB_MOVIE", "title", "movie/popular"),
    TV("TMDB_TV", "name", "tv/popular");

    private final String platformName;
    private final String titleField;
    private final String apiPath;
}


