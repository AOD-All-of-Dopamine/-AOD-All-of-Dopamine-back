package com.example.shared.repository;

import java.time.LocalDate;
import java.util.List;

/**
 * /api/works 목록 조회 필터 축 (동적 조립 입력).
 * null · 빈 리스트 · 공백 문자열 = 해당 축 꺼짐 → SQL에 조건이 아예 붙지 않는다.
 */
public record WorksFilterCriteria(
        String domain,
        List<String> genres,
        List<String> platforms,
        String keyword,
        LocalDate releaseFrom,
        LocalDate releaseTo,
        String status,
        List<String> weekdays,
        List<String> ageRatings,
        Integer reviewCountMin,
        /** 게임 탭 "리뷰 많은 순"(Steam 리뷰 수 · game_contents.review_count) — 게임 외 도메인에선 무시 (2026-10-04 트렌드 · 탐색) */
        boolean steamReviewSort
) {
    public WorksFilterCriteria(String domain, List<String> genres, List<String> platforms, String keyword,
                               LocalDate releaseFrom, LocalDate releaseTo, String status, List<String> weekdays,
                               List<String> ageRatings, Integer reviewCountMin) {
        this(domain, genres, platforms, keyword, releaseFrom, releaseTo, status, weekdays, ageRatings, reviewCountMin, false);
    }
}
