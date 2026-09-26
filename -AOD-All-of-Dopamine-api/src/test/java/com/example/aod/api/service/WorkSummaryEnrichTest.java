package com.example.AOD.api.service;

import com.example.AOD.api.dto.WorkSummaryDTO;
import com.example.shared.entity.PlatformData;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 목록 카드 보강 — 적은 표본 점수를 숨기는 데 쓰는 투표 수 · 리뷰 수 (탐색 가벼운 카드 2026-09-26). */
class WorkSummaryEnrichTest {

    private static PlatformData pd(String platform, Map<String, Object> attrs) {
        PlatformData data = new PlatformData();
        data.setPlatformName(platform);
        data.setAttributes(new HashMap<>(attrs));
        return data;
    }

    @Test
    void tmdbRatingAndVoteCount() {
        WorkSummaryDTO dto = new WorkSummaryDTO();
        WorkApiService.applyTmdbRating(dto, List.of(pd("TMDB_MOVIE", Map.of("rating", 7.4, "vote_count", 2760))));
        assertThat(dto.getExternalRating()).isEqualTo(7.4);
        assertThat(dto.getExternalVoteCount()).isEqualTo(2760);
    }

    @Test
    void tmdbWithoutVoteCountLeavesNull() {
        WorkSummaryDTO dto = new WorkSummaryDTO();
        WorkApiService.applyTmdbRating(dto, List.of(pd("TMDB_TV", Map.of("rating", 8.0))));
        assertThat(dto.getExternalRating()).isEqualTo(8.0);
        assertThat(dto.getExternalVoteCount()).isNull();
    }

    @Test
    void tmdbNonNumericVoteCountIgnored() {
        WorkSummaryDTO dto = new WorkSummaryDTO();
        WorkApiService.applyTmdbRating(dto, List.of(pd("TMDB_MOVIE", Map.of("vote_count", "많음"))));
        assertThat(dto.getExternalVoteCount()).isNull();
        assertThat(dto.getExternalRating()).isNull();
    }

    @Test
    void steamReviewCountAndPct() {
        WorkSummaryDTO dto = new WorkSummaryDTO();
        WorkApiService.applySteamReview(dto, List.of(pd("Steam", Map.of("review_summary",
                Map.of("review_score_desc", "Very Positive", "total_positive", 94, "total_reviews", 100)))));
        assertThat(dto.getSteamReviewDesc()).isEqualTo("Very Positive");
        assertThat(dto.getSteamPositivePct()).isEqualTo(94);
        assertThat(dto.getSteamReviewCount()).isEqualTo(100);
    }

    @Test
    void steamNoReviewsHasZeroCountAndNoPct() {
        WorkSummaryDTO dto = new WorkSummaryDTO();
        WorkApiService.applySteamReview(dto, List.of(pd("Steam", Map.of("review_summary",
                Map.of("review_score_desc", "No user reviews", "total_positive", 0, "total_reviews", 0)))));
        assertThat(dto.getSteamReviewCount()).isZero();
        assertThat(dto.getSteamPositivePct()).isNull();
    }

    @Test
    void steamWithoutSummaryLeavesNull() {
        WorkSummaryDTO dto = new WorkSummaryDTO();
        WorkApiService.applySteamReview(dto, List.of(pd("Steam", Map.of())));
        assertThat(dto.getSteamReviewCount()).isNull();
        assertThat(dto.getSteamReviewDesc()).isNull();
    }
}
