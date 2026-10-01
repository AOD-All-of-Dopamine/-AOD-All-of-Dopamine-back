-- ============================================================================
-- V11: 게임 세로 표지 (2026-10-01)
-- 설계: 프론트 docs/superpowers/specs/2026-10-01-game-portrait-cover-design.md
-- 크롤러(ddl-auto update)가 먼저 떠서 열을 만들었어도 IF NOT EXISTS 라 멱등 — 엔티티 타입을 여기와 같게 둔다.
-- ============================================================================

-- Steam 라이브러리 캡슐(600×900) 주소. 다른 도메인은 poster_image_url 이 이미 세로라 비워 둔다.
ALTER TABLE IF EXISTS contents ADD COLUMN IF NOT EXISTS portrait_image_url varchar(1000);

-- 표지를 마지막으로 확인한 시각 — 표지가 없다고 확인한 게임을 매일 다시 묻지 않는다.
ALTER TABLE IF EXISTS game_contents ADD COLUMN IF NOT EXISTS portrait_checked_at timestamptz;

-- 처음 채우는 구간(확인 안 한 게임을 리뷰 순으로)만 받친다. 다시 확인 구간은 하루 1회라 정렬로 충분하다.
CREATE INDEX IF NOT EXISTS idx_game_contents_portrait_unchecked
    ON game_contents (review_count DESC NULLS LAST) WHERE portrait_checked_at IS NULL;
