package com.example.AOD.ranking.service;

import com.example.AOD.ranking.dto.RankingResponse;
import com.example.AOD.ranking.mapper.RankingMapper;
import com.example.shared.entity.ExternalRanking;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** 직전 순위 기준 — 3일 안 가장 최근 / 네이버웹툰은 7일 전 같은 요일 / 없으면 기준 없음. */
class RankingHistoryStoreTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RankingHistoryStore store = new RankingHistoryStore(jdbc);

    private static ExternalRanking row(String platform, String psid, int rank, String fetchedAt) {
        ExternalRanking r = new ExternalRanking();
        r.setPlatform(platform);
        r.setPlatformSpecificId(psid);
        r.setRanking(rank);
        r.setFetchedAt(Instant.parse(fetchedAt));
        return r;
    }

    private void ranksOn(String platform, LocalDate day, Map<String, Integer> ranks) {
        doAnswer(inv -> {
            RowCallbackHandler h = inv.getArgument(1);
            for (var e : ranks.entrySet()) {
                java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                given(rs.getString("platform_specific_id")).willReturn(e.getKey());
                given(rs.getInt("ranking")).willReturn(e.getValue());
                h.processRow(rs);
            }
            return null;
        }).when(jdbc).query(eq(RankingHistoryStore.RANKS_SQL), any(RowCallbackHandler.class), eq(platform), eq(Date.valueOf(day)));
    }

    @Test
    void latestWithinThreeDaysAndKstDate() {
        // 2026-10-03T20:00Z = 10-04 05:00 KST → 오늘 10-04
        List<ExternalRanking> rows = List.of(row("Steam", "730", 1, "2026-10-03T20:00:00Z"), row("Steam", "999", 2, "2026-10-03T20:00:00Z"));
        given(jdbc.queryForObject(eq(RankingHistoryStore.LATEST_BEFORE_SQL), eq(Date.class), eq("Steam"),
                eq(Date.valueOf("2026-10-04")), eq(Date.valueOf("2026-10-01")))).willReturn(Date.valueOf("2026-10-02"));
        ranksOn("Steam", LocalDate.of(2026, 10, 2), Map.of("730", 3));

        Map<String, RankingHistoryStore.Baseline> b = store.baselines(rows);
        assertThat(b.get("Steam").baseDate()).isEqualTo(LocalDate.of(2026, 10, 2));

        // 매퍼: 730 은 3 → 1, 999 는 기준에 없음 → NEW
        List<RankingResponse> out = new RankingMapper().toResponseList(rows, b);
        assertThat(out.get(0).getPreviousRanking()).isEqualTo(3);
        assertThat(out.get(0).getRankBaseDate()).isEqualTo("2026-10-02");
        assertThat(out.get(1).getPreviousRanking()).isNull();
        assertThat(out.get(1).getRankBaseDate()).isEqualTo("2026-10-02");
    }

    @Test
    void noBaselineMeansNoDate() {
        List<ExternalRanking> rows = List.of(row("TMDB_TV", "1", 1, "2026-10-03T20:00:00Z"));
        given(jdbc.queryForObject(eq(RankingHistoryStore.LATEST_BEFORE_SQL), eq(Date.class), any(), any(), any())).willReturn(null);
        List<RankingResponse> out = new RankingMapper().toResponseList(rows, store.baselines(rows));
        assertThat(out.get(0).getRankBaseDate()).isNull();
        assertThat(out.get(0).getPreviousRanking()).isNull();
    }

    @Test
    void webtoonComparesSameWeekdayLastWeek() {
        List<ExternalRanking> rows = List.of(row("NaverWebtoon", "w1", 1, "2026-10-03T20:00:00Z"));
        given(jdbc.queryForObject(eq(RankingHistoryStore.EXISTS_DAY_SQL), eq(Integer.class), eq("NaverWebtoon"),
                eq(Date.valueOf("2026-09-27")))).willReturn(98);
        ranksOn("NaverWebtoon", LocalDate.of(2026, 9, 27), Map.of("w1", 4));

        RankingHistoryStore.Baseline b = store.baselines(rows).get("NaverWebtoon");
        assertThat(b.baseDate()).isEqualTo(LocalDate.of(2026, 9, 27));
        assertThat(b.ranks()).containsEntry("w1", 4);

        // 7일 전 기록이 없으면 기준 없음(어제와 비교하지 않는다)
        given(jdbc.queryForObject(eq(RankingHistoryStore.EXISTS_DAY_SQL), eq(Integer.class), eq("NaverWebtoon"), any())).willReturn(0);
        assertThat(store.baselines(rows).get("NaverWebtoon").baseDate()).isNull();
    }
}
