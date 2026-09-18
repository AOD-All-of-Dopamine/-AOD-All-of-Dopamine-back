package com.example.AOD.recommend.log;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

/** LogWriter 가 JDBC 배치로 쓰는 로그 1행. sql() 이 같은 레코드끼리 한 배치로 묶인다 (REC_TAB_DESIGN §5-8). */
public interface LogRecord {

    /** `?` 자리표시자를 쓰는 완전한 INSERT 문. 같은 테이블의 레코드는 같은 문자열을 돌려줘야 한다. */
    String sql();

    void bind(PreparedStatement ps) throws SQLException;

    static void setUuid(PreparedStatement ps, int i, UUID v) throws SQLException {
        if (v == null) ps.setNull(i, Types.OTHER); else ps.setObject(i, v);
    }

    static void setLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BIGINT); else ps.setLong(i, v);
    }

    static void setString(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) ps.setNull(i, Types.VARCHAR); else ps.setString(i, v);
    }

    static void setTs(PreparedStatement ps, int i, OffsetDateTime v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP_WITH_TIMEZONE); else ps.setObject(i, v);
    }
}
