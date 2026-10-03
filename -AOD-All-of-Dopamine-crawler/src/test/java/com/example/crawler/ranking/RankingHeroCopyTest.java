package com.example.crawler.ranking;

import com.example.shared.entity.ExternalRanking;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 랭킹 갱신 때 히어로 칸 복사 — 확인한 칸만 덮고(없음이면 null), 확인 안 한 칸은 옛 값 유지. */
class RankingHeroCopyTest {

    private static ExternalRanking old() {
        ExternalRanking r = new ExternalRanking();
        r.setBackdropUrl("old-bd"); r.setLogoUrl("old-logo"); r.setLogoLang("ko");
        r.setQuoteText("옛 인용"); r.setQuoteAuthor("옛 작성자"); r.setQuoteVotes(1); r.setQuoteHours(2);
        r.setQuoteUrl("old-url"); r.setQuoteReviewId("old-id");
        return r;
    }

    @Test
    void uncheckedKeepsOldValues() {
        ExternalRanking to = old();
        RankingUpsertHelper.copyHeroFields(new ExternalRanking(), to);
        assertThat(to.getBackdropUrl()).isEqualTo("old-bd");
        assertThat(to.getLogoUrl()).isEqualTo("old-logo");
        assertThat(to.getQuoteText()).isEqualTo("옛 인용");
    }

    @Test
    void checkedOverwritesIncludingNull() {
        ExternalRanking from = new ExternalRanking();
        from.setBackdropChecked(true); from.setBackdropUrl("new-bd");
        from.setLogoChecked(true);                       // 없다고 확인
        from.setQuoteChecked(true); from.setQuoteText("새 인용"); from.setQuoteReviewId("new-id");
        ExternalRanking to = old();

        RankingUpsertHelper.copyHeroFields(from, to);

        assertThat(to.getBackdropUrl()).isEqualTo("new-bd");
        assertThat(to.getLogoUrl()).isNull();
        assertThat(to.getLogoLang()).isNull();
        assertThat(to.getQuoteText()).isEqualTo("새 인용");
        assertThat(to.getQuoteAuthor()).isNull();
        assertThat(to.getQuoteReviewId()).isEqualTo("new-id");
    }
}
