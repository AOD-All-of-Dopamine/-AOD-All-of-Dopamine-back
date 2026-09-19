package com.example.AOD.recommend.log;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

/** aod_log.event 1행 (REC_TAB_DESIGN §5-3). event_id 로 전역 중복 제거된다. */
public record EventLogRecord(
        UUID eventId, OffsetDateTime serverTs, String eventType, String origin,
        Long userId, UUID anonId, UUID sessionId, Long contentId, UUID requestId, UUID impressionId,
        String surface, String payloadJson, OffsetDateTime clientTs, String appVersion, String device)
        implements DedupedLogRecord {

    public static final String SQL =
            "INSERT INTO aod_log.event (event_id, server_ts, event_type, origin, user_id, anon_id, session_id, "
          + "content_id, request_id, impression_id, surface, payload, client_ts, app_version, device) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)";

    @Override
    public String sql() {
        return SQL;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        ps.setObject(1, eventId);
        ps.setObject(2, serverTs);
        ps.setString(3, eventType);
        ps.setString(4, origin);
        LogRecord.setLong(ps, 5, userId);
        LogRecord.setUuid(ps, 6, anonId);
        LogRecord.setUuid(ps, 7, sessionId);
        LogRecord.setLong(ps, 8, contentId);
        LogRecord.setUuid(ps, 9, requestId);
        LogRecord.setUuid(ps, 10, impressionId);
        LogRecord.setString(ps, 11, surface);
        ps.setString(12, payloadJson == null ? "{}" : payloadJson);
        LogRecord.setTs(ps, 13, clientTs);
        LogRecord.setString(ps, 14, appVersion);
        LogRecord.setString(ps, 15, device);
    }
}
