package com.example.crawler.contents.game.steam.portrait;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 세로 표지 동기화의 DB 쪽 — 대상 고르기 · 묶음 저장. 엔티티를 거치지 않는다(JDBC) —
 * 그래서 {@code Content} · {@code GameContent} 에 {@code @DynamicUpdate} 를 둬 수집 병합이 이 열들을 덮지 않게 했다.
 */
@Repository
public class SteamPortraitStore {

    /** 다시 확인 주기. */
    static final int RECHECK_DAYS = 30;

    /**
     * 작품마다 Steam 행 하나(가장 먼저 수집된 행 = header 의 출처) · 숫자 appid 만.
     * 확인한 적 없는 것 먼저(그 안에선 리뷰 많은 게임부터), 다음은 오래 전에 확인한 것부터.
     */
    static final String TARGETS_SQL = """
            SELECT content_id, appid FROM (
              SELECT DISTINCT ON (c.content_id)
                     c.content_id, pd.platform_specific_id AS appid, g.portrait_checked_at, g.review_count
                FROM contents c
                JOIN game_contents g  ON g.content_id = c.content_id
                JOIN platform_data pd ON pd.content_id = c.content_id AND pd.platform_name = 'Steam'
               WHERE pd.platform_specific_id ~ '^[0-9]{1,18}$'
                 AND (g.portrait_checked_at IS NULL OR g.portrait_checked_at < now() - make_interval(days => ?))
               ORDER BY c.content_id, pd.platform_data_id
            ) t
            ORDER BY (portrait_checked_at IS NOT NULL),
                     CASE WHEN portrait_checked_at IS NULL THEN review_count END DESC NULLS LAST,
                     portrait_checked_at,
                     content_id
            LIMIT ?
            """;

    static final String UPDATE_PORTRAIT_SQL =
            "UPDATE contents SET portrait_image_url = ? WHERE content_id = ? AND portrait_image_url IS DISTINCT FROM ?";
    static final String UPDATE_CHECKED_SQL =
            "UPDATE game_contents SET portrait_checked_at = ? WHERE content_id = ?";

    private final JdbcTemplate jdbc;

    public SteamPortraitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Target(long contentId, long appId) { }

    /** 확인 결과 한 줄. {@code portraitUrl} 이 null 이면 표지 없음 — 기존 주소는 지우지 않는다. */
    public record Checked(long contentId, String portraitUrl) { }

    public List<Target> targets(int limit) {
        return jdbc.query(TARGETS_SQL,
                (rs, i) -> new Target(rs.getLong("content_id"), Long.parseLong(rs.getString("appid"))),
                RECHECK_DAYS, limit);
    }

    /** 묶음 하나 = 짧은 트랜잭션 하나. 표지가 있는 것만 주소를 쓰고, 모두 확인 시각을 남긴다. */
    @Transactional
    public void save(List<Checked> checked, Instant now) {
        if (checked.isEmpty()) return;
        List<Object[]> covers = new ArrayList<>();
        List<Object[]> stamps = new ArrayList<>();
        Timestamp ts = Timestamp.from(now);
        for (Checked c : checked) {
            if (c.portraitUrl() != null) covers.add(new Object[]{c.portraitUrl(), c.contentId(), c.portraitUrl()});
            stamps.add(new Object[]{ts, c.contentId()});
        }
        if (!covers.isEmpty()) jdbc.batchUpdate(UPDATE_PORTRAIT_SQL, covers);
        jdbc.batchUpdate(UPDATE_CHECKED_SQL, stamps);
    }
}
