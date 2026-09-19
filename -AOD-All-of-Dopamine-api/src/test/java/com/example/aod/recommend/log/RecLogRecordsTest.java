package com.example.AOD.recommend.log;

import com.example.AOD.recommend.context.RecContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RecLogRecordsTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CHAIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID IMPRESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final OffsetDateTime SERVED_AT = OffsetDateTime.parse("2026-09-19T12:00:00Z");

    private static RecRequestLogRecord requestRecord(UUID anonId, UUID sessionId) {
        return new RecRequestLogRecord(REQUEST_ID, SERVED_AT, CHAIN_ID, 2,
                7L, anonId, sessionId, "rec_tab", "all",
                List.of(1L, 2L), List.of("like", "bookmark"), List.of(9L), List.of(8L), List.of(5L), List.of(4L),
                "{\"rec_ranker\":\"control\"}", "{\"backend\":\"dev\"}", false, null,
                List.of("tmdb"), 412, null, null);
    }

    @Test
    void requestSqlTargetsRecRequestWithCasts() {
        String sql = requestRecord(UUID.randomUUID(), UUID.randomUUID()).sql();

        assertTrue(sql.startsWith("INSERT INTO aod_log.rec_request ("), sql);
        assertTrue(sql.contains("CAST(? AS bigint[])"), "배열은 리터럴 + CAST 로 넘긴다");
        assertTrue(sql.contains("CAST(? AS text[])"));
        assertTrue(sql.contains("CAST(? AS jsonb)"));
        assertEquals(23, sql.chars().filter(c -> c == '?').count(), "컬럼 23개");
    }

    @Test
    void requestBindsArraysAsPostgresLiterals() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        UUID anonId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        requestRecord(anonId, sessionId).bind(ps);

        verify(ps).setObject(1, REQUEST_ID);
        verify(ps).setObject(2, SERVED_AT);
        verify(ps).setObject(3, CHAIN_ID);
        verify(ps).setInt(4, 2);
        verify(ps).setLong(5, 7L);
        verify(ps).setObject(6, anonId);
        verify(ps).setObject(7, sessionId);
        verify(ps).setString(8, "rec_tab");
        verify(ps).setString(9, "all");
        verify(ps).setString(10, "{1,2}");                       // seed_ids
        verify(ps).setString(11, "{\"like\",\"bookmark\"}");     // seed_sources
        verify(ps).setString(12, "{9}");                          // disliked_ids
        verify(ps).setString(13, "{8}");                          // excluded_ids
        verify(ps).setString(14, "{5}");                          // seen_ids
        verify(ps).setString(15, "{4}");                          // dropped_seed_ids
        verify(ps).setString(16, "{\"rec_ranker\":\"control\"}"); // experiments
        verify(ps).setString(17, "{\"backend\":\"dev\"}");        // versions
        verify(ps).setBoolean(18, false);
        verify(ps).setNull(19, Types.VARCHAR);                    // fallback_reason
        verify(ps).setString(20, "{\"tmdb\"}");                   // partial
        verify(ps).setInt(21, 412);
        verify(ps).setNull(22, Types.VARCHAR);                    // app_version
        verify(ps).setNull(23, Types.VARCHAR);                    // device
    }

    @Test
    void missingAnonOrSessionBecomesNilUuid() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        RecRequestLogRecord record = requestRecord(null, null);

        assertEquals(RecRequestLogRecord.NIL_UUID, record.anonId());
        assertEquals(RecRequestLogRecord.NIL_UUID, record.sessionId());
        assertEquals("00000000-0000-0000-0000-000000000000", RecRequestLogRecord.NIL_UUID.toString());

        record.bind(ps);
        verify(ps).setObject(6, RecRequestLogRecord.NIL_UUID);
        verify(ps).setObject(7, RecRequestLogRecord.NIL_UUID);
    }

    @Test
    void nullLatencyAndEmptyArraysAreHandled() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);

        new RecRequestLogRecord(REQUEST_ID, SERVED_AT, CHAIN_ID, 0, null, null, null, "rec_tab", "game",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                "{}", "{\"backend\":\"dev\"}", true, "anonymous", List.of(), null, "1.0.0", "desktop").bind(ps);

        verify(ps).setNull(5, Types.BIGINT);     // user_id
        verify(ps).setString(10, "{}");
        verify(ps).setBoolean(18, true);
        verify(ps).setString(19, "anonymous");
        verify(ps).setNull(21, Types.INTEGER);   // latency_ms
        verify(ps).setString(22, "1.0.0");
        verify(ps).setString(23, "desktop");
    }

    @Test
    void itemServedSqlAndBinding() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        RecItemServedLogRecord served = new RecItemServedLogRecord(IMPRESSION_ID, SERVED_AT, REQUEST_ID,
                11L, "steam", "730", 0, "content_sim", "like", 42L,
                "{\"final\":1.19}", "steam.v1", false, 1.0f, null, true, null);

        assertTrue(served.sql().startsWith("INSERT INTO aod_log.rec_item_served ("));
        assertEquals(17, served.sql().chars().filter(c -> c == '?').count(), "컬럼 17개");
        assertTrue(served.sql().contains("CAST(? AS jsonb)"));

        served.bind(ps);

        verify(ps).setObject(1, IMPRESSION_ID);
        verify(ps).setObject(2, SERVED_AT);
        verify(ps).setObject(3, REQUEST_ID);
        verify(ps).setLong(4, 11L);
        verify(ps).setString(5, "steam");
        verify(ps).setString(6, "730");
        verify(ps).setInt(7, 0);
        verify(ps).setString(8, "content_sim");
        verify(ps).setString(9, "like");
        verify(ps).setLong(10, 42L);
        verify(ps).setString(11, "{\"final\":1.19}");
        verify(ps).setString(12, "steam.v1");
        verify(ps).setBoolean(13, false);
        verify(ps).setFloat(14, 1.0f);
        verify(ps).setNull(15, Types.VARCHAR);   // interleave_team
        verify(ps).setBoolean(16, true);
        verify(ps).setNull(17, Types.VARCHAR);   // dropped_reason
    }

    @Test
    void droppedItemUsesZeroContentIdWhenTheWorkIsUnknown() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);

        new RecItemServedLogRecord(IMPRESSION_ID, SERVED_AT, REQUEST_ID, 0L, "steam", "999", 3,
                "content_sim", null, null, "{}", null, false, 1.0f, null, false, "not_in_db").bind(ps);

        verify(ps).setLong(4, 0L);
        verify(ps).setNull(9, Types.VARCHAR);    // reason_type
        verify(ps).setNull(10, Types.BIGINT);    // reason_seed_id
        verify(ps).setString(11, "{}");
        verify(ps).setNull(12, Types.VARCHAR);   // factor_schema
        verify(ps).setBoolean(16, false);
        verify(ps).setString(17, "not_in_db");
    }

    @Test
    void recordsFromTheSameTableShareOneSqlString() {
        RecItemServedLogRecord a = new RecItemServedLogRecord(UUID.randomUUID(), SERVED_AT, REQUEST_ID, 1L,
                "steam", "1", 0, "content_sim", null, null, "{}", null, false, 1.0f, null, true, null);
        RecItemServedLogRecord b = new RecItemServedLogRecord(UUID.randomUUID(), SERVED_AT, REQUEST_ID, 2L,
                "tmdb", "movie_2", 1, "content_sim", null, null, "{}", null, false, 1.0f, null, false, "adult");

        // LogWriter 는 sql() 이 같은 레코드끼리 배치로 묶는다 — 같은 문자열 인스턴스여야 배치가 쪼개지지 않는다.
        assertEquals(a.sql(), b.sql());
        assertEquals(RecItemServedLogRecord.SQL, a.sql());
    }

    @Test
    void notInterestedChangedIsAServerEvent() {
        LogQueue queue = new LogQueue(new SimpleMeterRegistry());
        RecEventRecorder recorder = new RecEventRecorder(queue, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC));
        UUID impressionId = UUID.randomUUID();

        recorder.notInterestedChanged(7L, 42L, true, new RecContext("rec_tab", null, impressionId, null, null));
        recorder.notInterestedChanged(7L, 42L, false, RecContext.EMPTY);

        List<LogRecord> out = new ArrayList<>();
        queue.drainTo(out, 10);
        EventLogRecord on = (EventLogRecord) out.get(0);
        EventLogRecord off = (EventLogRecord) out.get(1);
        assertEquals("not_interested_changed", on.eventType());
        assertEquals("server", on.origin());
        assertEquals(7L, on.userId());
        assertEquals(42L, on.contentId());
        assertEquals(impressionId, on.impressionId());
        assertEquals("rec_tab", on.surface());
        assertTrue(on.payloadJson().contains("\"state\":\"on\""));
        assertTrue(off.payloadJson().contains("\"state\":\"off\""));
    }
}
