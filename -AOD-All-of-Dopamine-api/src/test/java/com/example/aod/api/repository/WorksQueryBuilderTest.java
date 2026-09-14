package com.example.AOD.api.repository;

import com.example.shared.repository.WorksFilterCriteria;
import com.example.shared.repository.WorksQueryBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * findWorks 동적 조립 계약 (troubleshooting/07).
 *
 * 원칙: DB에는 "실제로 켜진 조건만" 보낸다. 구 WORKS_FILTER의 `(:p IS NULL OR …)` 스위치는
 * 바인딩 파라미터 상황에서 플래너의 세미조인 변환을 봉인하고 행 수 추정을 붕괴시켜
 * GAME 19만 행 전수 스캔 + 행별 프로브 17만 번을 강제했다. 여기서는 그 스위치가
 * SQL 텍스트에 아예 등장하지 않음을 지킨다.
 */
class WorksQueryBuilderTest {

    private static final String BASE =
            "SELECT c.* FROM contents c WHERE c.domain = :domain AND c.is_adult = false";
    private static final String ORDER =
            "ORDER BY c.release_date DESC NULLS LAST, c.content_id ASC";

    private static WorksFilterCriteria game(Integer reviewCountMin) {
        return new WorksFilterCriteria("GAME", null, null, null, null, null, null, null, null, reviewCountMin);
    }

    private static String norm(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    @Test
    void noFilters_sendsOnlyDomainAndAdultExclusion() {
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(game(null));
        String sql = norm(b.sql());

        assertEquals(BASE + " " + ORDER, sql);
        assertEquals(Map.of("domain", "GAME"), b.params());
    }

    @Test
    void reviewCountMin_addsTopLevelExistsWithoutNullSwitch() {
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(game(1000));
        String sql = norm(b.sql());

        assertEquals(BASE
                + " AND EXISTS (SELECT 1 FROM game_contents g"
                + " WHERE g.content_id = c.content_id AND g.review_count >= :reviewCountMin)"
                + " " + ORDER, sql);
        assertFalse(sql.contains("IS NULL"), "IS NULL OR 스위치는 SQL에 등장하면 안 됨: " + sql);
        assertEquals(1000, b.params().get("reviewCountMin"));
    }

    @Test
    void genresAndPlatforms_useArrayOperatorsWithArrayParams() {
        WorksFilterCriteria c = new WorksFilterCriteria("GAME",
                List.of("액션", "RPG"), List.of("Steam"), null, null, null, null, null, null, null);
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(c);
        String sql = norm(b.sql());

        assertTrue(sql.contains(" AND c.genres @> CAST(:genres AS text[])"), sql);
        assertTrue(sql.contains(" AND c.platforms && CAST(:platforms AS text[])"), sql);
        assertArrayEquals(new String[]{"액션", "RPG"}, (String[]) b.params().get("genres"));
        assertArrayEquals(new String[]{"Steam"}, (String[]) b.params().get("platforms"));
    }

    @Test
    void keyword_bindsIlikePatternOnBothTitles() {
        WorksFilterCriteria c = new WorksFilterCriteria("GAME",
                null, null, "zelda", null, null, null, null, null, null);
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(c);
        String sql = norm(b.sql());

        assertTrue(sql.contains(" AND (c.master_title ILIKE :keyword OR c.original_title ILIKE :keyword)"), sql);
        assertEquals("%zelda%", b.params().get("keyword"));
    }

    @Test
    void releaseRange_bindsDatesIndependently() {
        WorksFilterCriteria fromOnly = new WorksFilterCriteria("MOVIE",
                null, null, null, LocalDate.of(2025, 1, 1), null, null, null, null, null);
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(fromOnly);
        String sql = norm(b.sql());

        assertTrue(sql.contains(" AND c.release_date >= :releaseFrom"), sql);
        assertFalse(sql.contains(":releaseTo"), sql);
        assertEquals(LocalDate.of(2025, 1, 1), b.params().get("releaseFrom"));

        WorksFilterCriteria both = new WorksFilterCriteria("MOVIE",
                null, null, null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null, null, null, null);
        String sql2 = norm(WorksQueryBuilder.build(both).sql());
        assertTrue(sql2.contains(" AND c.release_date >= :releaseFrom AND c.release_date <= :releaseTo"), sql2);
    }

    @Test
    void webtoonAxes_existsContainsOnlySetConditions() {
        WorksFilterCriteria statusOnly = new WorksFilterCriteria("WEBTOON",
                null, null, null, null, null, "ONGOING", null, null, null);
        String sql = norm(WorksQueryBuilder.build(statusOnly).sql());
        assertTrue(sql.contains(" AND EXISTS (SELECT 1 FROM webtoon_contents w"
                + " WHERE w.content_id = c.content_id AND w.status = :status)"), sql);
        assertFalse(sql.contains("weekday"), sql);
        assertFalse(sql.contains("age_rating"), sql);

        WorksFilterCriteria dayAndAge = new WorksFilterCriteria("WEBTOON",
                null, null, null, null, null, null, List.of("MON", "TUE"), List.of("ALL"), null);
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(dayAndAge);
        String sql2 = norm(b.sql());
        assertTrue(sql2.contains(" AND EXISTS (SELECT 1 FROM webtoon_contents w"
                + " WHERE w.content_id = c.content_id"
                + " AND w.weekday = ANY(CAST(:weekdays AS text[]))"
                + " AND w.age_rating = ANY(CAST(:ageRatings AS text[])))"), sql2);
        assertFalse(sql2.contains("w.status"), sql2);
        assertArrayEquals(new String[]{"MON", "TUE"}, (String[]) b.params().get("weekdays"));
    }

    @Test
    void emptyListsAndBlankStrings_areTreatedAsOff() {
        WorksFilterCriteria c = new WorksFilterCriteria("GAME",
                List.of(), List.of(), "   ", null, null, "  ", List.of(), List.of(), null);
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(c);

        assertEquals(BASE + " " + ORDER, norm(b.sql()));
        assertEquals(Map.of("domain", "GAME"), b.params());
    }

    @Test
    void countSql_sharesWhereAndOmitsOrderBy() {
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(game(1000));
        String count = norm(b.countSql());

        assertEquals("SELECT COUNT(*) FROM contents c WHERE c.domain = :domain AND c.is_adult = false"
                + " AND EXISTS (SELECT 1 FROM game_contents g"
                + " WHERE g.content_id = c.content_id AND g.review_count >= :reviewCountMin)", count);
        assertFalse(count.contains("ORDER BY"));
    }

    @Test
    void adultExclusion_isAlwaysPresentInBothQueries() {
        WorksFilterCriteria c = new WorksFilterCriteria("GAME",
                List.of("액션"), List.of("Steam"), "k", LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1),
                null, null, null, 10);
        WorksQueryBuilder.Built b = WorksQueryBuilder.build(c);

        assertTrue(norm(b.sql()).contains("c.is_adult = false"));
        assertTrue(norm(b.countSql()).contains("c.is_adult = false"));
    }
}
