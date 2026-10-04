package com.example.crawler.ranking;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 순위 일별 기록 — KST 날짜 · 그날 행 교체 · 10행 미만 건너뜀. */
class RankingDailyRecorderTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    private final RankingDailyRecorder recorder;

    {
        given(tm.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        recorder = new RankingDailyRecorder(jdbc, tm);
    }

    private static List<RankingDailyRecorder.Row> rows(int n) {
        List<RankingDailyRecorder.Row> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(new RankingDailyRecorder.Row("id" + i, i % 2 == 0 ? (long) i : null, i));
        return out;
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesTheDayInKst() {
        // 2026-10-03T19:30Z = 10-04 04:30 KST
        int n = recorder.record("Steam", Instant.parse("2026-10-03T19:30:00Z"), rows(12));

        assertThat(n).isEqualTo(12);
        verify(jdbc).update(RankingDailyRecorder.DELETE_DAY_SQL, Date.valueOf(LocalDate.of(2026, 10, 4)), "Steam");
        ArgumentCaptor<List<Object[]>> args = ArgumentCaptor.forClass(List.class);
        verify(jdbc).batchUpdate(eq(RankingDailyRecorder.INSERT_SQL), args.capture());
        assertThat(args.getValue()).hasSize(12);
        assertThat(args.getValue().get(0)).containsExactly(Date.valueOf(LocalDate.of(2026, 10, 4)), "Steam", "id1", null, 1);
        verify(jdbc).update(RankingDailyRecorder.DELETE_OLD_SQL, "Steam", Date.valueOf(LocalDate.of(2026, 10, 4).minusDays(400)));
    }

    @Test
    void tooFewRowsAreNotRecorded() {
        assertThat(recorder.record("NaverSeries", Instant.now(), rows(9))).isZero();
        verify(jdbc, never()).batchUpdate(anyString(), anyList());
        verify(jdbc, never()).update(eq(RankingDailyRecorder.DELETE_DAY_SQL), any(Object[].class));
    }
}
