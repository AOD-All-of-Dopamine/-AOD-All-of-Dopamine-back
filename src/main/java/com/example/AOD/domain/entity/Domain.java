package com.example.AOD.domain.entity;

public enum Domain {
    MOVIE,      // TMDB 영화 (기존 AV에서 분리)
    TV,         // TMDB TV쇼 (기존 AV에서 분리)
    GAME, 
    WEBTOON, 
    WEBNOVEL,
    
    @Deprecated // 마이그레이션 후 제거 예정 - MOVIE, TV로 분리됨
    AV
}