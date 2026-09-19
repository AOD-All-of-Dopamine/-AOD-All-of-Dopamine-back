package com.example.AOD.recommend.log;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * aod_log.rec_request 1행 (REC_TAB_DESIGN §5-3).
 * 대체 응답도 남긴다 — 빼면 노출·클릭 분모가 틀어진다(§2-7).
 *
 * 배열은 CAST(? AS bigint[])·CAST(? AS text[]) + 리터럴 문자열(SqlArrays)로 넘긴다:
 * JDBC 배치에서 java.sql.Array 를 만들면 커넥션을 꺼내야 하고 해제 책임이 생긴다.
 * jsonb 는 기존 EventLogRecord 와 같은 CAST(? AS jsonb) 방식.
 *
 * anon_id·session_id 는 NOT NULL 인데 헤더가 없을 수 있다 → nil UUID 로 채운다.
 */
public record RecRequestLogRecord(
        UUID requestId, OffsetDateTime servedAt, UUID chainId, int pageDepth,
        Long userId, UUID anonId, UUID sessionId, String surface, String tab,
        List<Long> seedIds, List<String> seedSources, List<Long> dislikedIds,
        List<Long> excludedIds, List<Long> seenIds, List<Long> droppedSeedIds,
        String experimentsJson, String versionsJson, boolean fallback, String fallbackReason,
        List<String> partial, Integer latencyMs, String appVersion, String device)
        implements LogRecord {

    /** 헤더에 anon_id·session_id 가 없을 때 쓰는 값. 분석에서 "미상"으로 가려낸다. */
    public static final UUID NIL_UUID = new UUID(0L, 0L);

    public static final String SQL =
            "INSERT INTO aod_log.rec_request (request_id, served_at, chain_id, page_depth, user_id, anon_id, "
          + "session_id, surface, tab, seed_ids, seed_sources, disliked_ids, excluded_ids, seen_ids, "
          + "dropped_seed_ids, experiments, versions, fallback, fallback_reason, partial, latency_ms, "
          + "app_version, device) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS bigint[]), CAST(? AS text[]), CAST(? AS bigint[]), "
          + "CAST(? AS bigint[]), CAST(? AS bigint[]), CAST(? AS bigint[]), CAST(? AS jsonb), CAST(? AS jsonb), "
          + "?, ?, CAST(? AS text[]), ?, ?, ?)";

    public RecRequestLogRecord {
        anonId = anonId == null ? NIL_UUID : anonId;
        sessionId = sessionId == null ? NIL_UUID : sessionId;
    }

    @Override
    public String sql() {
        return SQL;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        ps.setObject(1, requestId);
        ps.setObject(2, servedAt);
        ps.setObject(3, chainId);
        ps.setInt(4, pageDepth);
        LogRecord.setLong(ps, 5, userId);
        ps.setObject(6, anonId);
        ps.setObject(7, sessionId);
        ps.setString(8, surface);
        ps.setString(9, tab);
        ps.setString(10, SqlArrays.bigints(seedIds));
        ps.setString(11, SqlArrays.texts(seedSources));
        ps.setString(12, SqlArrays.bigints(dislikedIds));
        ps.setString(13, SqlArrays.bigints(excludedIds));
        ps.setString(14, SqlArrays.bigints(seenIds));
        ps.setString(15, SqlArrays.bigints(droppedSeedIds));
        ps.setString(16, experimentsJson == null ? "{}" : experimentsJson);
        ps.setString(17, versionsJson == null ? "{}" : versionsJson);
        ps.setBoolean(18, fallback);
        LogRecord.setString(ps, 19, fallbackReason);
        ps.setString(20, SqlArrays.texts(partial));
        if (latencyMs == null) ps.setNull(21, Types.INTEGER); else ps.setInt(21, latencyMs);
        LogRecord.setString(ps, 22, appVersion);
        LogRecord.setString(ps, 23, device);
    }
}
