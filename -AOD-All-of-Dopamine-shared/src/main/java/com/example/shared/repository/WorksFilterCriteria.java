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
        Integer reviewCountMin
) {
}
