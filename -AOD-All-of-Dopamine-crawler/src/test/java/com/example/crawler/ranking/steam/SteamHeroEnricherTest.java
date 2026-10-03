package com.example.crawler.ranking.steam;

import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.Assets;
import com.example.crawler.contents.game.steam.portrait.SteamPortraitClient.SteamPortraitException;
import com.example.crawler.ranking.steam.SteamHeroClient.SteamReview;
import com.example.shared.entity.ExternalRanking;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Steam 랭킹 행에 히어로 그림 · 리뷰 한 줄 붙이기 — 후보만 · 실패는 확인 안 함(옛 값 유지). */
class SteamHeroEnricherTest {

    private final SteamPortraitClient portrait = mock(SteamPortraitClient.class);
    private final SteamHeroClient hero = mock(SteamHeroClient.class);
    private final SteamHeroEnricher enricher = new SteamHeroEnricher(portrait, hero);

    private static ExternalRanking row(long appId, int rank, boolean candidate) {
        ExternalRanking r = new ExternalRanking();
        r.setPlatform("Steam");
        r.setPlatformSpecificId(String.valueOf(appId));
        r.setRanking(rank);
        r.setRatingLabel(candidate ? "Very Positive" : "Mixed");
        r.setRatingScore(candidate ? 0.95 : 0.6);
        r.setRatingCount(50_000);
        return r;
    }

    private static SteamReview review(String id, String text, int votes) {
        return new SteamReview(id, "7656119", text, true, votes, 0, 0.8, 600);
    }

    @Test
    void candidatesGetLogoAndQuoteOthersAreClearedAndBackdropForAll() {
        ExternalRanking hit = row(1091500, 3, true), miss = row(730, 1, false), far = row(570, 31, true);
        given(portrait.fetchAssets(anyList())).willReturn(Map.of(
                1091500L, new Assets(1091500, "https://cdn/hero1.jpg"), 730L, new Assets(730, null)));
        given(hero.fetchLogo(1091500)).willReturn(Optional.of(new SteamHeroClient.Logo("https://cdn/logo_koreana.png", "ko")));
        given(hero.fetchKoreanReviews(1091500)).willReturn(List.of(
                review("1", "이 게임 진짜 좆같이 재밌어요 다들 꼭 해보세요 진심으로", 300),
                review("2", "진짜 내 인생 게임 총 맞아도 되니까 나이트 시티에서 살고싶음", 45)));
        given(hero.fetchPersonaName("7656119")).willReturn("나이트시티주민");

        enricher.enrich(List.of(hit, miss, far));

        assertThat(hit.getBackdropUrl()).isEqualTo("https://cdn/hero1.jpg");
        assertThat(hit.isBackdropChecked()).isTrue();
        assertThat(hit.getLogoUrl()).isEqualTo("https://cdn/logo_koreana.png");
        assertThat(hit.getLogoLang()).isEqualTo("ko");
        assertThat(hit.getQuoteText()).isEqualTo("진짜 내 인생 게임 총 맞아도 되니까 나이트 시티에서 살고싶음");
        assertThat(hit.getQuoteAuthor()).isEqualTo("나이트시티주민");
        assertThat(hit.getQuoteVotes()).isEqualTo(45);
        assertThat(hit.getQuoteHours()).isEqualTo(10);
        assertThat(hit.getQuoteReviewId()).isEqualTo("2");
        assertThat(hit.getQuoteUrl()).isEqualTo("https://steamcommunity.com/profiles/7656119/recommended/1091500/");
        // 후보 아님(평가 미달 · 31위) — 로고 · 인용은 "없음으로 확인", 호출하지 않는다
        for (ExternalRanking r : List.of(miss, far)) {
            assertThat(r.isLogoChecked()).isTrue();
            assertThat(r.isQuoteChecked()).isTrue();
            assertThat(r.getQuoteText()).isNull();
        }
        verify(hero, never()).fetchKoreanReviews(730);
        verify(hero, never()).fetchKoreanReviews(570);
        // 배경: 없다고 확인(730) vs 응답에 없음(570 — 확인 안 함)
        assertThat(miss.isBackdropChecked()).isTrue();
        assertThat(miss.getBackdropUrl()).isNull();
        assertThat(far.isBackdropChecked()).isFalse();
    }

    @Test
    void failuresLeaveFieldsUncheckedSoOldValuesStay() {
        ExternalRanking hit = row(1091500, 1, true);
        given(portrait.fetchAssets(anyList())).willThrow(new SteamPortraitException("timeout", null));
        given(hero.fetchLogo(anyLong())).willThrow(new IllegalStateException("connect timed out"));
        given(hero.fetchKoreanReviews(anyLong())).willThrow(new IllegalStateException("429"));

        enricher.enrich(List.of(hit));

        assertThat(hit.isBackdropChecked()).isFalse();
        assertThat(hit.isLogoChecked()).isFalse();
        assertThat(hit.isQuoteChecked()).isFalse();
    }

    @Test
    void noPassingReviewMeansCheckedEmpty() {
        ExternalRanking hit = row(1091500, 1, true);
        given(portrait.fetchAssets(anyList())).willReturn(Map.of());
        given(hero.fetchLogo(anyLong())).willReturn(Optional.empty());
        given(hero.fetchKoreanReviews(anyLong())).willReturn(List.of(review("1", "갓겜", 500)));

        enricher.enrich(List.of(hit));

        assertThat(hit.isLogoChecked()).isTrue();
        assertThat(hit.getLogoUrl()).isNull();
        assertThat(hit.isQuoteChecked()).isTrue();
        assertThat(hit.getQuoteText()).isNull();
        verify(hero, never()).fetchPersonaName("7656119");
    }
}
