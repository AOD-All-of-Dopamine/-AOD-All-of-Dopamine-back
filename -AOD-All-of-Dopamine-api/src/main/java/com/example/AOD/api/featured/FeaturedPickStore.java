package com.example.AOD.api.featured;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

/**
 * featured_pick 표 (V10) — 날짜마다 한 작품. insert-if-absent 라 재시작 · 인스턴스가 여럿이어도 하루 결과가 같다.
 * 행을 고치면 그날 작품이 바뀐다(수동 지정).
 */
@Repository
public class FeaturedPickStore {

    static final String FIND_SQL =
            "SELECT featured_date, content_id, platform, ranking, basis, rating_score, rating_count, rating_label "
          + "FROM featured_pick WHERE featured_date = ?";
    static final String INSERT_SQL =
            "INSERT INTO featured_pick (featured_date, content_id, platform, ranking, basis, rating_score, rating_count, rating_label) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (featured_date) DO NOTHING";
    static final String RECENT_SQL =
            "SELECT content_id FROM featured_pick WHERE featured_date > ? AND featured_date < ?";

    private static final RowMapper<FeaturedPick> ROW_MAPPER = (rs, i) -> new FeaturedPick(
            rs.getDate("featured_date").toLocalDate(),
            rs.getLong("content_id"),
            rs.getString("platform"),
            rs.getInt("ranking"),
            rs.getString("basis"),
            (Double) rs.getObject("rating_score"),
            (Integer) rs.getObject("rating_count"),
            rs.getString("rating_label"));

    private final JdbcTemplate jdbc;

    public FeaturedPickStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<FeaturedPick> find(LocalDate date) {
        List<FeaturedPick> rows = jdbc.query(FIND_SQL, ROW_MAPPER, Date.valueOf(date));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** 이미 그날 행이 있으면 아무것도 하지 않는다 — 호출자는 다시 읽어 실제 저장된 작품을 쓴다. */
    public void insertIfAbsent(FeaturedPick pick) {
        jdbc.update(INSERT_SQL, Date.valueOf(pick.date()), pick.contentId(), pick.platform(), pick.ranking(),
                pick.basis(), pick.ratingScore(), pick.ratingCount(), pick.ratingLabel());
    }

    /** (date - days, date) 사이에 뽑힌 작품 — 반복 제외용. */
    public Set<Long> recentContentIds(LocalDate date, int days) {
        return new HashSet<>(jdbc.queryForList(RECENT_SQL, Long.class,
                Date.valueOf(date.minusDays(days)), Date.valueOf(date)));
    }

    public record FeaturedPick(LocalDate date, long contentId, String platform, int ranking, String basis,
                               Double ratingScore, Integer ratingCount, String ratingLabel) {
    }
}
