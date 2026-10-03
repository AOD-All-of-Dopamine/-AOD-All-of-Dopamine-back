package com.example.shared.featured;

import com.example.shared.entity.ExternalRanking;

/**
 * 홈 "오늘의 작품" 후보 문턱 — API(고르기)와 크롤러(그림 · 인용을 받을 행 고르기)가 같이 쓴다.
 * 설계: 프론트 docs/superpowers/specs/2026-09-26-home-featured-today-design.md · 2026-10-03-home-featured-hero-design.md
 */
public final class FeaturedGates {

    private FeaturedGates() { }

    public static final int MAX_RANKING = 30;
    public static final double GAME_MIN_POSITIVE = 0.90;
    public static final int GAME_MIN_REVIEWS = 10_000;
    public static final double TMDB_MIN_RATING = 7.5;
    public static final int TMDB_MIN_VOTES = 500;

    /** 고평가 문턱 — 반올림 전 원자료로 판정한다. 랭킹 순위는 보지 않는다. */
    public static boolean passesRating(ExternalRanking row) {
        return passesRating(row, row.getPlatform());
    }

    public static boolean passesRating(ExternalRanking row, String platform) {
        Double score = row.getRatingScore();
        Integer count = row.getRatingCount();
        if (score == null || count == null) return false;
        if ("Steam".equals(platform)) {
            String label = row.getRatingLabel();
            return label != null && label.endsWith("Positive") && score >= GAME_MIN_POSITIVE && count >= GAME_MIN_REVIEWS;
        }
        return score >= TMDB_MIN_RATING && count >= TMDB_MIN_VOTES;
    }

    /** 순위 · 평가 문턱을 모두 넘는지 — 크롤러가 로고 · 인용을 받을 행. */
    public static boolean isCandidate(ExternalRanking row) {
        return row.getRanking() != null && row.getRanking() <= MAX_RANKING && passesRating(row);
    }
}
