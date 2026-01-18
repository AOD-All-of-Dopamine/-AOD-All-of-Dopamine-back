package com.example.crawler.common.queue;

/**
 * 크롤링 작업 타입
 */
public enum JobType {
    // Steam 게임
    STEAM_GAME,
    
    // TMDB 영화/TV
    TMDB_MOVIE,
    TMDB_TV,
    
    // 네이버 웹툰
    NAVER_WEBTOON,
    NAVER_WEBTOON_FINISHED,
    
    // 네이버 시리즈 소설
    NAVER_SERIES_NOVEL,
    
    // 카카오페이지
    KAKAO_PAGE_NOVEL,
    KAKAO_PAGE_WEBTOON,
    
    // 향후 확장 가능
    // NETFLIX_CONTENT,
    // WATCHA_CONTENT,
    // etc...
}
