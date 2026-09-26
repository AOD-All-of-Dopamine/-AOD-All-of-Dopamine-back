-- ============================================================================
-- V10: 홈 "오늘의 작품" (2026-09-26)
-- 근거: 프론트 docs/superpowers/specs/2026-09-26-home-featured-today-design.md
--
-- 1) external_ranking 에 랭킹을 받을 때의 신선한 평가를 둔다. 콘텐츠 쪽(platform_data) 평점 · 투표 수는
--    수집 시점에 굳어 있어(TMDB 는 최근 7일 출시작만 다시 받는다, Steam 요약은 사실상 재수집 없음)
--    신작은 문턱을 못 넘고 나빠진 작품이 옛 값으로 보인다. 랭킹 수집은 매일 이 값을 이미 읽는다.
--    external_ranking 은 크롤러가 JPA 로 만든 표라 없을 수도 있다 — IF EXISTS.
-- 2) featured_pick: 날짜마다 한 작품. insert-if-absent 라 재시작 · 인스턴스가 여럿이어도 하루 결과가 같고,
--    45일 반복 제외 · 수동 지정(행 수정)의 근거가 된다.
-- ============================================================================

ALTER TABLE IF EXISTS external_ranking ADD COLUMN IF NOT EXISTS rating_score double precision;
ALTER TABLE IF EXISTS external_ranking ADD COLUMN IF NOT EXISTS rating_count integer;
ALTER TABLE IF EXISTS external_ranking ADD COLUMN IF NOT EXISTS rating_label varchar(64);
ALTER TABLE IF EXISTS external_ranking ADD COLUMN IF NOT EXISTS fetched_at timestamptz;

CREATE TABLE IF NOT EXISTS featured_pick (
    featured_date  date         PRIMARY KEY,           -- 05:00 KST 에 넘어가는 날짜
    content_id     bigint       NOT NULL,
    platform       varchar(32)  NOT NULL,              -- Steam · TMDB_MOVIE · TMDB_TV
    ranking        integer      NOT NULL,
    basis          varchar(16)  NOT NULL,              -- steam · tmdb
    rating_score   double precision,
    rating_count   integer,
    rating_label   varchar(64),
    created_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_featured_pick_content ON featured_pick (content_id, featured_date);
