package com.example.AOD.api.featured;

import com.example.AOD.api.dto.WorkSummaryDTO;

/** GET /api/works/featured-today 응답. {@code reason} 의 평가 값은 뽑을 때의 신선한 값이라 그날 안에서 바뀌지 않는다. */
public record FeaturedWorkDTO(String date, WorkSummaryDTO work, Reason reason) {

    /**
     * @param ratingScore 게임: 긍정 비율(0~1) · 영화/시리즈: TMDB 평점
     * @param ratingLabel 게임: Steam 판정(영문) · 그 밖 null
     */
    public record Reason(String platform, int ranking, String basis,
                         Double ratingScore, Integer ratingCount, String ratingLabel) {
    }
}
