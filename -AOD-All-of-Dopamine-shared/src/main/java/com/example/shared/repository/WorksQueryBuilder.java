package com.example.shared.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * /api/works 목록 조회 SQL 동적 조립기 (troubleshooting/07 §8 ①).
 *
 * 원칙: <b>DB에는 실제로 켜진 조건만 보낸다.</b>
 * 구 WORKS_FILTER의 {@code (:p IS NULL OR 조건)} 스위치는 바인딩 파라미터 상황에서
 * 플래너가 값을 모른 채 계획을 짜게 만들어 (1) EXISTS의 세미조인 변환을 봉인하고
 * (2) 행 수 추정을 붕괴시켜, GAME 19만 행 전수 스캔 + 행별 프로브 17만 번을 강제했다.
 * 여기서는 꺼진 축이 SQL 텍스트에 아예 등장하지 않으므로 플래너는 항상 확정된 조건만 본다.
 *
 * <ul>
 *   <li>genres: {@code @>} 포함(AND) — GIN 인덱스</li>
 *   <li>platforms: {@code &&} 겹침(OR) — GIN 인덱스</li>
 *   <li>keyword: 제목/원제 부분 일치 (ILIKE)</li>
 *   <li>releaseFrom/To: 출시일 범위</li>
 *   <li>status/weekdays/ageRatings: webtoon_contents EXISTS (켜진 조건만 내부에 포함)</li>
 *   <li>reviewCountMin: game_contents EXISTS (최상위 AND → 세미조인 변환 가능)</li>
 *   <li>is_adult = false 고정, ORDER BY release_date DESC NULLS LAST, content_id ASC 고정</li>
 * </ul>
 */
public final class WorksQueryBuilder {

    /** 조립 결과: 본 쿼리 · count 쿼리 · 바인딩 파라미터 (이름 → 값). */
    public record Built(String sql, String countSql, Map<String, Object> params) {
    }

    private static final String SELECT = "SELECT c.* FROM contents c WHERE ";
    private static final String COUNT = "SELECT COUNT(*) FROM contents c WHERE ";
    private static final String ORDER_BY = " ORDER BY c.release_date DESC NULLS LAST, c.content_id ASC";

    private WorksQueryBuilder() {
    }

    public static Built build(WorksFilterCriteria c) {
        Objects.requireNonNull(c.domain(), "domain은 필수 (필터 조회는 도메인 탭 안에서만)");

        StringBuilder where = new StringBuilder("c.domain = :domain AND c.is_adult = false");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("domain", c.domain());

        if (notEmpty(c.genres())) {
            where.append(" AND c.genres @> CAST(:genres AS text[])");
            params.put("genres", toArray(c.genres()));
        }
        if (notEmpty(c.platforms())) {
            where.append(" AND c.platforms && CAST(:platforms AS text[])");
            params.put("platforms", toArray(c.platforms()));
        }
        if (notBlank(c.keyword())) {
            where.append(" AND (c.master_title ILIKE :keyword OR c.original_title ILIKE :keyword)");
            params.put("keyword", "%" + c.keyword().trim() + "%");
        }
        if (c.releaseFrom() != null) {
            where.append(" AND c.release_date >= :releaseFrom");
            params.put("releaseFrom", c.releaseFrom());
        }
        if (c.releaseTo() != null) {
            where.append(" AND c.release_date <= :releaseTo");
            params.put("releaseTo", c.releaseTo());
        }

        boolean status = notBlank(c.status());
        boolean weekdays = notEmpty(c.weekdays());
        boolean ageRatings = notEmpty(c.ageRatings());
        if (status || weekdays || ageRatings) {
            where.append(" AND EXISTS (SELECT 1 FROM webtoon_contents w WHERE w.content_id = c.content_id");
            if (status) {
                where.append(" AND w.status = :status");
                params.put("status", c.status().trim());
            }
            if (weekdays) {
                where.append(" AND w.weekday = ANY(CAST(:weekdays AS text[]))");
                params.put("weekdays", toArray(c.weekdays()));
            }
            if (ageRatings) {
                where.append(" AND w.age_rating = ANY(CAST(:ageRatings AS text[]))");
                params.put("ageRatings", toArray(c.ageRatings()));
            }
            where.append(")");
        }

        if (c.reviewCountMin() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM game_contents g"
                    + " WHERE g.content_id = c.content_id AND g.review_count >= :reviewCountMin)");
            params.put("reviewCountMin", c.reviewCountMin());
        }

        return new Built(SELECT + where + ORDER_BY, COUNT + where, params);
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String[] toArray(List<String> list) {
        return list.toArray(new String[0]);
    }
}
