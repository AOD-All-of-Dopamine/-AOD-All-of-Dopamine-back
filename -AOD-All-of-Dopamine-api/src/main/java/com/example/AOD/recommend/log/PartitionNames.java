package com.example.AOD.recommend.log;

import java.time.YearMonth;
import java.util.Optional;

/** 월 파티션 이름·경계 규칙. V8 마이그레이션의 DO 블록과 같아야 한다: {table}_yYYYYmMM, 경계는 UTC. */
public final class PartitionNames {

    private PartitionNames() { }

    public static String of(String table, YearMonth month) {
        return String.format("%s_y%04dm%02d", table, month.getYear(), month.getMonthValue());
    }

    /** 파티션 이름에서 월을 읽는다. 규칙에 안 맞으면(DEFAULT 등) empty. */
    public static Optional<YearMonth> monthOf(String table, String partitionName) {
        String prefix = table + "_y";
        if (partitionName == null || !partitionName.startsWith(prefix)) return Optional.empty();
        String rest = partitionName.substring(prefix.length());   // "2026m10"
        int m = rest.indexOf('m');
        if (m != 4 || rest.length() != 7) return Optional.empty();
        try {
            return Optional.of(YearMonth.of(Integer.parseInt(rest.substring(0, 4)), Integer.parseInt(rest.substring(5))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static String boundLiteral(YearMonth month) {
        return month.atDay(1) + "T00:00:00Z";
    }
}
