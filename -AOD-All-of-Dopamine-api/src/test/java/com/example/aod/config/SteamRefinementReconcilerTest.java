package com.example.AOD.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 부팅 reconcile 러너 (2026-08 Steam 정제 I-4) — 쿼리 정확성 수준 검증.
 * 실제 UPDATE 효과는 PG 전용 문법(jsonb @>)이라 여기선 SQL 계약(판정 기준·멱등 가드)과
 * 실행 배선만 지킨다.
 */
@ExtendWith(MockitoExtension.class)
class SteamRefinementReconcilerTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private SteamRefinementReconciler reconciler;

    @Test
    void reconcileRunsBothIdempotentUpdates() {
        given(jdbcTemplate.update(SteamRefinementReconciler.ADULT_FLAG_SQL)).willReturn(2);
        given(jdbcTemplate.update(SteamRefinementReconciler.REVIEW_COUNT_BACKFILL_SQL)).willReturn(5);

        reconciler.reconcile();

        verify(jdbcTemplate).update(SteamRefinementReconciler.ADULT_FLAG_SQL);
        verify(jdbcTemplate).update(SteamRefinementReconciler.REVIEW_COUNT_BACKFILL_SQL);
    }

    @Test
    void adultFlagSqlUsesSexualDescriptorCriteriaOnly() {
        String sql = SteamRefinementReconciler.ADULT_FLAG_SQL;
        // 판정 기준: content_descriptor_ids에 3/4 (성적 콘텐츠) — required_age는 기각된 기준
        assertTrue(sql.contains("content_descriptor_ids"), "descriptor 기반 판정이어야 함");
        assertTrue(sql.contains("@> '3'::jsonb") && sql.contains("@> '4'::jsonb"),
                "성적 디스크립터 3/4 둘 다 검사해야 함");
        assertFalse(sql.contains("required_age"), "required_age 기준은 기각됨 (폭력성 18금 과차단)");
        // 멱등 가드: 이미 true인 행은 건드리지 않음
        assertTrue(sql.contains("c.is_adult = false"), "멱등 가드(is_adult=false) 누락");
        assertTrue(sql.contains("pd.platform_name = 'Steam'"), "Steam 한정이어야 함");
    }

    @Test
    void reviewCountSqlFillsOnlyMissingRows() {
        String sql = SteamRefinementReconciler.REVIEW_COUNT_BACKFILL_SQL;
        assertTrue(sql.contains("review_summary") && sql.contains("total_reviews"));
        assertTrue(sql.contains("g.review_count IS NULL"), "이미 채워진 행 보존(멱등) 가드 누락");
        assertTrue(sql.contains("~ '^[0-9]+$'"), "숫자 형태 가드 누락 (비정상 값 캐스팅 방지)");
    }
}
