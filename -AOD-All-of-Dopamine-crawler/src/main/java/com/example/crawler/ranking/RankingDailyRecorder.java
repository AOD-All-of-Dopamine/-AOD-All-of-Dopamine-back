package com.example.crawler.ranking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 외부 순위 일별 기록 (V13 external_ranking_daily) — 트렌드의 순위 변동 · NEW 근거.
 * 설계: 프론트 docs/superpowers/specs/2026-10-04-trend-explore-design.md (v2 결정 4).
 *
 * <ul>
 *   <li>그날(KST) · 그 플랫폼 행을 <b>지우고 다시 넣는다</b> — 재실행 · 수동 실행에도 마지막 한 벌이 남는다</li>
 *   <li>{@value #MIN_ROWS}행 미만이면 기록하지 않는다 — 망가진 수집이 기준이 되지 않게</li>
 *   <li>엔티티 없이 JdbcTemplate — 크롤러 ddl-auto 가 표를 다른 모양으로 먼저 만들지 않게</li>
 *   <li>보관 {@value #RETENTION_DAYS}일 — 기록 직후 지난 행을 지운다</li>
 * </ul>
 */
@Slf4j
@Component
public class RankingDailyRecorder {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MIN_ROWS = 10;
    static final int RETENTION_DAYS = 400;

    static final String DELETE_DAY_SQL = "DELETE FROM external_ranking_daily WHERE snapshot_date = ? AND platform = ?";
    static final String INSERT_SQL = "INSERT INTO external_ranking_daily "
            + "(snapshot_date, platform, platform_specific_id, content_id, ranking) VALUES (?, ?, ?, ?, ?) "
            + "ON CONFLICT (snapshot_date, platform, platform_specific_id) DO UPDATE "
            + "SET content_id = EXCLUDED.content_id, ranking = EXCLUDED.ranking";
    static final String DELETE_OLD_SQL = "DELETE FROM external_ranking_daily WHERE platform = ? AND snapshot_date < ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public RankingDailyRecorder(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 순위 한 행. contentId 는 아직 연결 전이면 null. */
    public record Row(String platformSpecificId, Long contentId, int ranking) { }

    /** @return 기록한 행 수 (건너뛰면 0) */
    public int record(String platform, Instant fetchedAt, List<Row> rows) {
        if (rows == null || rows.size() < MIN_ROWS) {
            log.warn("순위 기록 건너뜀 — {} {}행(< {})", platform, rows == null ? 0 : rows.size(), MIN_ROWS);
            return 0;
        }
        LocalDate day = snapshotDate(fetchedAt);
        Integer written = tx.execute(status -> {
            jdbc.update(DELETE_DAY_SQL, Date.valueOf(day), platform);
            List<Object[]> args = new ArrayList<>(rows.size());
            for (Row r : rows) args.add(new Object[]{Date.valueOf(day), platform, r.platformSpecificId(), r.contentId(), r.ranking()});
            jdbc.batchUpdate(INSERT_SQL, args);
            jdbc.update(DELETE_OLD_SQL, platform, Date.valueOf(day.minusDays(RETENTION_DAYS)));
            return args.size();
        });
        log.info("순위 기록 {} {} — {}행", platform, day, written);
        return written == null ? 0 : written;
    }

    static LocalDate snapshotDate(Instant fetchedAt) {
        return (fetchedAt == null ? Instant.now() : fetchedAt).atZone(KST).toLocalDate();
    }
}
