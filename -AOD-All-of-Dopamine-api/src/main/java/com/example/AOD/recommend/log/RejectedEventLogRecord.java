package com.example.AOD.recommend.log;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/** aod_log.rejected_event 1행 — 거절 이벤트 1% 표본. rawJson 은 유효한 JSON 이어야 한다. */
public record RejectedEventLogRecord(String rawJson, String reason, OffsetDateTime serverTs) implements LogRecord {

    public static final String SQL =
            "INSERT INTO aod_log.rejected_event (raw, reason, server_ts) VALUES (CAST(? AS jsonb), ?, ?)";

    @Override
    public String sql() {
        return SQL;
    }

    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        ps.setString(1, rawJson);
        ps.setString(2, reason);
        ps.setObject(3, serverTs);
    }
}
