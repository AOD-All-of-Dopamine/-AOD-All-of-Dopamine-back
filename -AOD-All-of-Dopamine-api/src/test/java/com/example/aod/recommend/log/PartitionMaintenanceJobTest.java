package com.example.AOD.recommend.log;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartitionMaintenanceJobTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-18T03:15:00Z"), ZoneOffset.UTC);
    private final PartitionMaintenanceJob job = new PartitionMaintenanceJob(jdbc, clock);

    @Test
    void namesAndBoundsMatchV8Convention() {
        assertEquals("event_y2026m10", PartitionNames.of("event", YearMonth.of(2026, 10)));
        assertEquals("2026-10-01T00:00:00Z", PartitionNames.boundLiteral(YearMonth.of(2026, 10)));
        assertEquals(Optional.of(YearMonth.of(2025, 8)), PartitionNames.monthOf("event", "event_y2025m08"));
        assertEquals(Optional.empty(), PartitionNames.monthOf("event", "event_default"));
        assertEquals(Optional.empty(), PartitionNames.monthOf("event", "event_seen"));
    }

    @Test
    void ensuresThisMonthAndTwoMonthsAheadForEveryPartitionedTable() {
        List<String> created = job.ensureFuturePartitions();

        assertEquals(12, created.size(), "테이블 4개 × (이번 달 + 2개월)");
        verify(jdbc, times(12)).execute(anyString());
        verify(jdbc).execute("CREATE TABLE IF NOT EXISTS aod_log.event_y2026m09 PARTITION OF aod_log.event "
                + "FOR VALUES FROM ('2026-09-01T00:00:00Z') TO ('2026-10-01T00:00:00Z')");
        verify(jdbc).execute("CREATE TABLE IF NOT EXISTS aod_log.client_agent_y2026m11 PARTITION OF aod_log.client_agent "
                + "FOR VALUES FROM ('2026-11-01T00:00:00Z') TO ('2026-12-01T00:00:00Z')");
    }

    @Test
    void dropsOnlyPartitionsOlderThanRetention() {
        when(jdbc.queryForList(anyString(), eq(String.class), eq("event")))
                .thenReturn(List.of("event_y2025m08", "event_y2025m09", "event_y2026m09", "event_default"));
        when(jdbc.queryForList(anyString(), eq(String.class), eq("client_agent")))
                .thenReturn(List.of("client_agent_y2026m05", "client_agent_y2026m06"));

        List<String> dropped = job.dropExpiredPartitions();

        // event 보관 12개월: 기준월 2025-09 → 그보다 이전인 2025-08 만. client_agent 3개월: 기준월 2026-06 → 2026-05 만.
        assertEquals(List.of("event_y2025m08", "client_agent_y2026m05"), dropped);
        verify(jdbc).execute("DROP TABLE IF EXISTS aod_log.event_y2025m08");
        verify(jdbc).execute("DROP TABLE IF EXISTS aod_log.client_agent_y2026m05");
        verify(jdbc, never()).execute("DROP TABLE IF EXISTS aod_log.event_default");
    }

    @Test
    void purgesExpiredChainsAndOldNotInterestedRows() {
        job.purgeRecTables();

        // 고정 시계 2026-09-18T03:15:00Z 기준: 체인 24시간 · 관심 없음 90일
        verify(jdbc).update("DELETE FROM aod_rec.rec_chain WHERE updated_at < ?",
                OffsetDateTime.parse("2026-09-17T03:15:00Z"));
        verify(jdbc).update("DELETE FROM aod_rec.not_interested WHERE created_at < ?",
                OffsetDateTime.parse("2026-06-20T03:15:00Z"));
    }
}
