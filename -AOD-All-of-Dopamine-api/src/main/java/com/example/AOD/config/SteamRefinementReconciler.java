package com.example.AOD.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Steam 정제 부팅 reconcile (2026-08, V6와 짝).
 *
 * 재발 창 방지: 수집 스킵(Executor)이 배포되기 전에 스테이징된 raw가 늦게 변환되거나,
 * 재크롤로 attr.content_descriptor_ids가 새로 채워진 기존 게임이 성적 콘텐츠로 판명되면
 * V6(1회 실행)만으로는 못 잡는다 — 부팅마다 멱등 UPDATE로 따라잡는 안전망.
 *
 * 판정 기준: content_descriptors.ids에 3(Adult Only Sexual Content) 또는
 * 4(Frequent Nudity or Sexual Content) — SteamGameExecutor의 수집 스킵 기준과 동일.
 * (required_age 기준은 기각 — 폭력성 18금까지 걸어 정반대로 작동)
 *
 * 관례: DatabaseIndexInitializer와 동일 — ApplicationReady 후 JdbcTemplate, 실패는 warn만
 * (다음 부팅에 재시도, 기동은 막지 않음).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SteamRefinementReconciler {

    /** 성적 디스크립터(3/4) 보유 콘텐츠 플래그 — V6 §2 철회 조건의 순방향, 멱등 */
    static final String ADULT_FLAG_SQL = """
            UPDATE contents c
               SET is_adult = true
              FROM platform_data pd
             WHERE pd.content_id = c.content_id
               AND pd.platform_name = 'Steam'
               AND ((pd.attributes -> 'content_descriptor_ids') @> '3'::jsonb
                    OR (pd.attributes -> 'content_descriptor_ids') @> '4'::jsonb)
               AND c.is_adult = false
            """;

    /** review_summary.total_reviews → game_contents.review_count 충전 — V6 §4와 동일, 멱등 */
    static final String REVIEW_COUNT_BACKFILL_SQL = """
            UPDATE game_contents g
               SET review_count = (pd.attributes -> 'review_summary' ->> 'total_reviews')::int
              FROM platform_data pd
             WHERE pd.content_id = g.content_id
               AND pd.platform_name = 'Steam'
               AND (pd.attributes -> 'review_summary' ->> 'total_reviews') ~ '^[0-9]+$'
               AND g.review_count IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        try {
            int flagged = jdbcTemplate.update(ADULT_FLAG_SQL);
            int filled = jdbcTemplate.update(REVIEW_COUNT_BACKFILL_SQL);
            log.info("Steam 정제 reconcile 완료 — is_adult 신규 플래그 {}건, review_count 충전 {}건", flagged, filled);
        } catch (Exception e) {
            log.warn("Steam 정제 reconcile 실패 (다음 부팅에 재시도): {}", e.getMessage());
        }
    }
}
