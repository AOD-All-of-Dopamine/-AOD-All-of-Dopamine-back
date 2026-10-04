-- ============================================================================
-- V13: 외부 순위 일별 기록 (2026-10-04 트렌드 — 순위 변동 · NEW)
-- 설계: 프론트 docs/superpowers/specs/2026-10-04-trend-explore-design.md (v2 결정 4 · 5)
-- external_ranking 은 그날 한 벌만 둔다(새 목록에 없는 행 삭제) — 어제와 비교하려면 따로 쌓아야 한다.
-- 크롤러가 순위 저장 뒤(트랜잭션 밖) 그날 · 그 플랫폼 행을 지우고 다시 넣는다(재실행에도 마지막 한 벌).
-- 엔티티 없이 JdbcTemplate 로만 쓴다 — 크롤러 ddl-auto 가 다른 모양으로 먼저 만들지 않게.
-- snapshot_date = 수집 시각(fetched_at)의 KST 날짜.
-- ============================================================================

CREATE TABLE IF NOT EXISTS external_ranking_daily (
    snapshot_date        date         NOT NULL,
    platform             varchar(255) NOT NULL,
    platform_specific_id varchar(255) NOT NULL,
    content_id           bigint,
    ranking              integer      NOT NULL,
    PRIMARY KEY (snapshot_date, platform, platform_specific_id)
);

-- 직전 순위 찾기(플랫폼 · 날짜) · 3단계 작품별 집계
CREATE INDEX IF NOT EXISTS idx_ranking_daily_platform_date ON external_ranking_daily (platform, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_ranking_daily_content ON external_ranking_daily (content_id, snapshot_date);

-- 기준선 — 지금 external_ranking 한 벌을 그 수집일로 심어 둔다(배포 다음 날부터 변동이 보이게)
INSERT INTO external_ranking_daily (snapshot_date, platform, platform_specific_id, content_id, ranking)
SELECT (er.fetched_at AT TIME ZONE 'Asia/Seoul')::date, er.platform, er.platform_specific_id, er.content_id, er.ranking
  FROM external_ranking er
 WHERE er.fetched_at IS NOT NULL AND er.ranking IS NOT NULL
ON CONFLICT DO NOTHING;
