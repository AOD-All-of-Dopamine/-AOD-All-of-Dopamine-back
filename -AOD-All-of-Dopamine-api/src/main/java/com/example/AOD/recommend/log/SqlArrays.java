package com.example.AOD.recommend.log;

import java.util.Collection;

/**
 * PostgreSQL 배열 리터럴 만들기.
 *
 * JDBC 배치(LogWriter)에서 java.sql.Array 를 만들면 PreparedStatement 에서 커넥션을 꺼내야 하고
 * Array 를 free() 할 책임이 생긴다. 기존 EventLogRecord 가 jsonb 를 CAST(? AS jsonb) + 문자열로
 * 넘기고 있으므로 배열도 같은 방식으로 맞춘다 — 순수 함수라 단위 테스트가 쉽다.
 */
public final class SqlArrays {

    private SqlArrays() { }

    /** bigint[] 리터럴. null 원소는 건너뛴다. */
    public static String bigints(Collection<Long> values) {
        if (values == null || values.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Long value : values) {
            if (value == null) continue;
            if (!first) sb.append(',');
            sb.append(value.longValue());
            first = false;
        }
        return sb.append('}').toString();
    }

    /** text[] 리터럴. 원소를 전부 큰따옴표로 감싸고 " 와 \ 만 이스케이프한다. null 원소는 건너뛴다. */
    public static String texts(Collection<String> values) {
        if (values == null || values.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String value : values) {
            if (value == null) continue;
            if (!first) sb.append(',');
            sb.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c == '"' || c == '\\') sb.append('\\');
                sb.append(c);
            }
            sb.append('"');
            first = false;
        }
        return sb.append('}').toString();
    }
}
