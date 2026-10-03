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
            "SELECT featured_date, content_id, platform, ranking, basis, rating_score, rating_count, rating_label, "
          + "backdrop_url, logo_url, logo_lang, quote_text, quote_author, quote_votes, quote_hours, quote_url, quote_review_id "
          + "FROM featured_pick WHERE featured_date = ?";
    static final String INSERT_SQL =
            "INSERT INTO featured_pick (featured_date, content_id, platform, ranking, basis, rating_score, rating_count, rating_label, "
          + "backdrop_url, logo_url, logo_lang, quote_text, quote_author, quote_votes, quote_hours, quote_url, quote_review_id) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (featured_date) DO NOTHING";
    /** 시즌 · 러닝타임 (히어로 정보 줄). */
    static final String SEASONS_SQL = "SELECT season_count FROM tv_contents WHERE content_id = ?";
    static final String RUNTIME_SQL = "SELECT runtime FROM movie_contents WHERE content_id = ?";
    /**
     * 우리 리뷰 후보 — 별점 4 이상 · 작성자 리뷰 3개 이상 · 작성자 첫 리뷰가 14일 이상 전(users 에 가입일이 없어 대신 쓴다).
     * 별점 높은 순 → 최신 순. 글 거름은 호출자(ReviewQuotes)가 한다.
     */
    static final String OUR_REVIEWS_SQL =
            "SELECT r.review_id, r.rating, r.review_content FROM reviews r "
          + "WHERE r.content_id = ? AND r.rating >= 4.0 AND r.review_content IS NOT NULL "
          + "AND r.user_id IN (SELECT user_id FROM reviews GROUP BY user_id "
          + "HAVING count(*) >= 3 AND min(created_at) <= now() - interval '14 days') "
          + "ORDER BY r.rating DESC, r.created_at DESC LIMIT 30";
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
            rs.getString("rating_label"),
            new Hero(rs.getString("backdrop_url"), rs.getString("logo_url"), rs.getString("logo_lang"),
                    rs.getString("quote_text"), rs.getString("quote_author"), (Integer) rs.getObject("quote_votes"),
                    (Integer) rs.getObject("quote_hours"), rs.getString("quote_url"), rs.getString("quote_review_id")));

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
        Hero h = pick.hero();
        jdbc.update(INSERT_SQL, Date.valueOf(pick.date()), pick.contentId(), pick.platform(), pick.ranking(),
                pick.basis(), pick.ratingScore(), pick.ratingCount(), pick.ratingLabel(),
                h.backdropUrl(), h.logoUrl(), h.logoLang(), h.quoteText(), h.quoteAuthor(), h.quoteVotes(),
                h.quoteHours(), h.quoteUrl(), h.quoteReviewId());
    }

    /** (date - days, date) 사이에 뽑힌 작품 — 반복 제외용. */
    public Set<Long> recentContentIds(LocalDate date, int days) {
        return new HashSet<>(jdbc.queryForList(RECENT_SQL, Long.class,
                Date.valueOf(date.minusDays(days)), Date.valueOf(date)));
    }

    public Integer seasons(long contentId) {
        List<Integer> rows = jdbc.queryForList(SEASONS_SQL, Integer.class, contentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Integer runtimeMinutes(long contentId) {
        List<Integer> rows = jdbc.queryForList(RUNTIME_SQL, Integer.class, contentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<OurReview> ourReviewCandidates(long contentId) {
        return jdbc.query(OUR_REVIEWS_SQL, (rs, i) -> new OurReview(rs.getLong("review_id"), rs.getDouble("rating"),
                rs.getString("review_content")), contentId);
    }

    public record OurReview(long reviewId, double rating, String text) { }

    /** 히어로 그림 · 외부 리뷰 한 줄 (V12) — 고를 때 랭킹 행에서 복사해 하루 고정. */
    public record Hero(String backdropUrl, String logoUrl, String logoLang, String quoteText, String quoteAuthor,
                       Integer quoteVotes, Integer quoteHours, String quoteUrl, String quoteReviewId) {
        public static final Hero EMPTY = new Hero(null, null, null, null, null, null, null, null, null);
    }

    public record FeaturedPick(LocalDate date, long contentId, String platform, int ranking, String basis,
                               Double ratingScore, Integer ratingCount, String ratingLabel, Hero hero) {
        public FeaturedPick {
            if (hero == null) hero = Hero.EMPTY;
        }

        public FeaturedPick(LocalDate date, long contentId, String platform, int ranking, String basis,
                            Double ratingScore, Integer ratingCount, String ratingLabel) {
            this(date, contentId, platform, ranking, basis, ratingScore, ratingCount, ratingLabel, Hero.EMPTY);
        }
    }
}
