package com.example.AOD.api.dto;

import java.util.List;

/**
 * /api/works 필터 축 묶음 (2026-08 확장).
 *
 * 필터 출처 규율: contents 마스터 컬럼 + 도메인 엔티티 컬럼만 —
 * platform_data.attributes(JSONB)는 필터 축 금지 (표시 전용).
 *
 * - genres: contents.genres 포함(AND) 매칭
 * - platforms: contents.platforms 겹침(OR) 매칭 — 수집 소스·OTT 공용
 * - releaseFrom/To: contents.release_date 범위 (yyyy-MM-dd)
 * - status/weekdays/ageRatings: webtoon_contents 도메인 컬럼 (타 도메인에선 무시)
 * - reviewCountMin: game_contents.review_count 하한 (2026-08 Steam 정제) —
 *   게임 외 도메인에 보내면 0건이므로 프론트가 게임 탭 한정으로 전송하는 계약
 */
public record WorkFilters(
        List<String> genres,
        List<String> platforms,
        String releaseFrom,
        String releaseTo,
        String status,
        List<String> weekdays,
        List<String> ageRatings,
        Integer reviewCountMin
) {
    public static WorkFilters none() {
        return new WorkFilters(null, null, null, null, null, null, null, null);
    }

    public boolean hasAny() {
        return notEmpty(genres) || notEmpty(platforms)
                || notBlank(releaseFrom) || notBlank(releaseTo)
                || notBlank(status) || notEmpty(weekdays) || notEmpty(ageRatings)
                || reviewCountMin != null;
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
