package com.example.AOD.recommend.log;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;

/** aod_log.client_agent 1행 — 세션당 1회. NOT EXISTS 가 재시작 뒤 중복을 막는다. */
public record ClientAgentLogRecord(UUID sessionId, OffsetDateTime firstSeen, String userAgent) implements LogRecord {

    public static final String SQL =
            "INSERT INTO aod_log.client_agent (session_id, first_seen, user_agent) "
          + "SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM aod_log.client_agent WHERE session_id = ?)";

    @Override
    public String sql() {
        return SQL;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        ps.setObject(1, sessionId);
        ps.setObject(2, firstSeen);
        ps.setString(3, userAgent);
        ps.setObject(4, sessionId);
    }
}
