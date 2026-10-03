package com.example.AOD.api.featured;

import com.example.AOD.api.dto.WorkSummaryDTO;

/**
 * GET /api/works/featured-today 응답. {@code reason} 의 평가 값은 뽑을 때의 신선한 값이라 그날 안에서 바뀌지 않는다.
 * 히어로(2026-10-03): {@code synopsis} · {@code facts} · {@code media} · {@code quote}(없으면 null).
 */
public record FeaturedWorkDTO(String date, WorkSummaryDTO work, Reason reason,
                              String synopsis, Facts facts, Media media, Quote quote) {

    public FeaturedWorkDTO(String date, WorkSummaryDTO work, Reason reason) {
        this(date, work, reason, null, new Facts(null, null), new Media(null, null, null), null);
    }

    /**
     * @param ratingScore 게임: 긍정 비율(0~1) · 영화/시리즈: TMDB 평점
     * @param ratingLabel 게임: Steam 판정(영문) · 그 밖 null
     */
    public record Reason(String platform, int ranking, String basis,
                         Double ratingScore, Integer ratingCount, String ratingLabel) {
    }

    /** 시리즈 시즌 수 · 영화 러닝타임(분). 그 밖은 null. */
    public record Facts(Integer seasons, Integer runtimeMinutes) { }

    /** 넓은 배경 · 투명 로고. 값이 null 일 수 있다. {@code logoLang} = ko | other. */
    public record Media(String backdropUrl, String logoUrl, String logoLang) { }

    /**
     * 리뷰 한 줄. {@code source} = OURS(우리 사이트) | STEAM.
     * STEAM: author(없으면 null) · votes · hours · url. OURS: rating (닉네임은 내지 않는다).
     */
    public record Quote(String source, String text, String author, Integer votes, Integer hours, Double rating, String url) { }
}
