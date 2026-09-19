package com.example.AOD.recommend.log;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * aod_log.rec_item_served 1행 (REC_TAB_DESIGN §5-3).
 * 서빙한 것은 is_served=true, 버퍼에서 탈락한 것은 is_served=false + dropped_reason
 * (not_in_db · adult · dup_content · over_k) — 무엇이 왜 빠졌는지 없으면 커버리지를 못 쫓는다.
 * 대체 응답은 platform="fallback" · corpus_key=content_id 문자열 · candidate_source="ranking_fallback".
 * content_id 는 NOT NULL 이라 DB 에 없어서 버린 후보는 0 으로 남긴다("작품 미상").
 */
public record RecItemServedLogRecord(
        UUID impressionId, OffsetDateTime servedAt, UUID requestId, Long contentId,
        String platform, String corpusKey, int rankPosition, String candidateSource,
        String reasonType, Long reasonSeedId, String scoreJson, String factorSchema,
        boolean isExploration, float propensity, String interleaveTeam,
        boolean isServed, String droppedReason)
        implements LogRecord {

    public static final String SQL =
            "INSERT INTO aod_log.rec_item_served (impression_id, served_at, request_id, content_id, platform, "
          + "corpus_key, rank_position, candidate_source, reason_type, reason_seed_id, score, factor_schema, "
          + "is_exploration, propensity, interleave_team, is_served, dropped_reason) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)";

    @Override
    public String sql() {
        return SQL;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        ps.setObject(1, impressionId);
        ps.setObject(2, servedAt);
        ps.setObject(3, requestId);
        ps.setLong(4, contentId == null ? 0L : contentId);
        ps.setString(5, platform);
        ps.setString(6, corpusKey);
        ps.setInt(7, rankPosition);
        ps.setString(8, candidateSource);
        LogRecord.setString(ps, 9, reasonType);
        LogRecord.setLong(ps, 10, reasonSeedId);
        ps.setString(11, scoreJson == null ? "{}" : scoreJson);
        LogRecord.setString(ps, 12, factorSchema);
        ps.setBoolean(13, isExploration);
        ps.setFloat(14, propensity);
        LogRecord.setString(ps, 15, interleaveTeam);
        ps.setBoolean(16, isServed);
        LogRecord.setString(ps, 17, droppedReason);
    }
}
