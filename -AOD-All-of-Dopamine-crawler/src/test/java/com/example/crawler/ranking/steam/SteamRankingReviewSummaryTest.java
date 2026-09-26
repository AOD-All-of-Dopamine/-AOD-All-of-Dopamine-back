package com.example.crawler.ranking.steam;

import com.example.crawler.contents.game.steam.SteamFetcher;
import com.example.crawler.ranking.RankingUpsertHelper;
import com.example.shared.entity.ExternalRanking;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** 랭킹 작품의 신선한 리뷰 요약 → rating_* (홈 "오늘의 작품"). */
class SteamRankingReviewSummaryTest {

    private final SteamFetcher fetcher = mock(SteamFetcher.class);
    private final SteamRankingService service =
            new SteamRankingService(mock(SteamRankingFetcher.class), mock(RankingUpsertHelper.class), fetcher);

    private static ExternalRanking ranking(String appId) {
        ExternalRanking r = new ExternalRanking();
        r.setPlatformSpecificId(appId);
        return r;
    }

    @Test
    void mapsRawRatioCountAndLabel() {
        given(fetcher.fetchReviewSummary(1145350L)).willReturn(Map.of(
                "review_score_desc", "Overwhelmingly Positive", "total_positive", 118_180, "total_reviews", 125_310));
        ExternalRanking r = ranking("1145350");

        service.attachReviewSummaries(List.of(r));

        assertThat(r.getRatingLabel()).isEqualTo("Overwhelmingly Positive");
        assertThat(r.getRatingCount()).isEqualTo(125_310);
        assertThat(r.getRatingScore()).isEqualTo(118_180d / 125_310d); // 반올림하지 않는다
    }

    @Test
    void failureLeavesNullsAndContinues() {
        given(fetcher.fetchReviewSummary(1L)).willThrow(new RuntimeException("timeout"));
        given(fetcher.fetchReviewSummary(2L)).willReturn(Map.of(
                "review_score_desc", "No user reviews", "total_positive", 0, "total_reviews", 0));
        ExternalRanking failed = ranking("1");
        ExternalRanking empty = ranking("2");

        service.attachReviewSummaries(List.of(failed, empty));

        assertThat(failed.getRatingScore()).isNull();
        assertThat(failed.getRatingCount()).isNull();
        assertThat(empty.getRatingCount()).isZero();
        assertThat(empty.getRatingScore()).isNull(); // 리뷰 0건이면 비율 없음
    }
}
