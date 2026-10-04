package com.example.AOD.ranking.service;

import com.example.shared.entity.ExternalRanking;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 직전 순위 (V13 external_ranking_daily) — 순위 변동 · NEW.
 * 설계: 프론트 docs/superpowers/specs/2026-10-04-trend-explore-design.md (v2 결정 5).
 *
 * <ul>
 *   <li>기준일 = 오늘(그 플랫폼 순위의 수집일, KST) 이전 가장 최근 기록 — {@value #MAX_GAP_DAYS}일 안(크롤 하루 실패에도 비교한다)</li>
 *   <li>네이버웹툰은 "오늘 요일 연재 목록" 순서라 <b>7일 전 같은 요일</b>과 비교한다</li>
 *   <li>기준일이 없으면 {@code baseDate == null} — 프론트는 변동 칸을 비운다(NEW 와 구분)</li>
 * </ul>
 */
@Repository
public class RankingHistoryStore {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final int MAX_GAP_DAYS = 3;
    static final String WEEKLY_PLATFORM = "NaverWebtoon";

    static final String LATEST_BEFORE_SQL =
            "SELECT max(snapshot_date) FROM external_ranking_daily WHERE platform = ? AND snapshot_date < ? AND snapshot_date >= ?";
    static final String EXISTS_DAY_SQL =
            "SELECT count(*) FROM external_ranking_daily WHERE platform = ? AND snapshot_date = ?";
    static final String RANKS_SQL =
            "SELECT platform_specific_id, ranking FROM external_ranking_daily WHERE platform = ? AND snapshot_date = ?";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public RankingHistoryStore(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    RankingHistoryStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** 플랫폼 하나의 기준. {@code baseDate == null} 이면 비교할 기록이 없다. */
    public record Baseline(LocalDate baseDate, Map<String, Integer> ranks) {
        static final Baseline NONE = new Baseline(null, Map.of());
    }

    /** 순위 행들이 속한 플랫폼마다 기준을 찾는다 (플랫폼당 쿼리 2번). */
    public Map<String, Baseline> baselines(List<ExternalRanking> rows) {
        Map<String, LocalDate> today = new HashMap<>();
        for (ExternalRanking r : rows) {
            LocalDate d = day(r.getFetchedAt());
            today.merge(r.getPlatform(), d, (a, b) -> a.isAfter(b) ? a : b);
        }
        Map<String, Baseline> out = new HashMap<>();
        for (String platform : new LinkedHashSet<>(today.keySet())) {
            out.put(platform, baseline(platform, today.get(platform)));
        }
        return out;
    }

    Baseline baseline(String platform, LocalDate today) {
        LocalDate base;
        if (WEEKLY_PLATFORM.equals(platform)) {
            LocalDate weekAgo = today.minusDays(7);
            Integer n = jdbc.queryForObject(EXISTS_DAY_SQL, Integer.class, platform, Date.valueOf(weekAgo));
            base = n != null && n > 0 ? weekAgo : null;
        } else {
            Date d = jdbc.queryForObject(LATEST_BEFORE_SQL, Date.class, platform, Date.valueOf(today),
                    Date.valueOf(today.minusDays(MAX_GAP_DAYS)));
            base = d == null ? null : d.toLocalDate();
        }
        if (base == null) return Baseline.NONE;
        Map<String, Integer> ranks = new HashMap<>();
        jdbc.query(RANKS_SQL, rs -> {
            ranks.put(rs.getString("platform_specific_id"), rs.getInt("ranking"));
        }, platform, Date.valueOf(base));
        return new Baseline(base, ranks);
    }

    LocalDate day(Instant fetchedAt) {
        return (fetchedAt == null ? clock.instant() : fetchedAt).atZone(KST).toLocalDate();
    }

}
