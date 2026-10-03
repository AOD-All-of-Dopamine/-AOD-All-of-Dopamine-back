-- ============================================================================
-- V12: 홈 "오늘의 작품" 히어로 — 배경 · 로고 · 리뷰 한 줄 (2026-10-03)
-- 설계: 프론트 docs/superpowers/specs/2026-10-03-home-featured-hero-design.md
-- external_ranking 은 크롤러가 채우고(문턱 통과 · 30위 이내만 로고 · 인용), 고를 때 featured_pick 에 복사해 하루 고정한다.
-- featured_pick 에는 외부 인용만 저장한다 — 우리 리뷰는 표시할 때 다시 읽는다(삭제 · 탈퇴 바로 반영).
-- 크롤러(ddl-auto update)가 먼저 떠서 열을 만들었어도 IF NOT EXISTS 라 멱등 — 엔티티 @Column 과 같은 타입.
-- ============================================================================

ALTER TABLE IF EXISTS external_ranking
  ADD COLUMN IF NOT EXISTS backdrop_url    varchar(1000),
  ADD COLUMN IF NOT EXISTS logo_url        varchar(1000),
  ADD COLUMN IF NOT EXISTS logo_lang       varchar(8),
  ADD COLUMN IF NOT EXISTS quote_text      varchar(400),
  ADD COLUMN IF NOT EXISTS quote_author    varchar(100),
  ADD COLUMN IF NOT EXISTS quote_votes     integer,
  ADD COLUMN IF NOT EXISTS quote_hours     integer,
  ADD COLUMN IF NOT EXISTS quote_url       varchar(1000),
  ADD COLUMN IF NOT EXISTS quote_review_id varchar(32);

ALTER TABLE IF EXISTS featured_pick
  ADD COLUMN IF NOT EXISTS backdrop_url    varchar(1000),
  ADD COLUMN IF NOT EXISTS logo_url        varchar(1000),
  ADD COLUMN IF NOT EXISTS logo_lang       varchar(8),
  ADD COLUMN IF NOT EXISTS quote_text      varchar(400),
  ADD COLUMN IF NOT EXISTS quote_author    varchar(100),
  ADD COLUMN IF NOT EXISTS quote_votes     integer,
  ADD COLUMN IF NOT EXISTS quote_hours     integer,
  ADD COLUMN IF NOT EXISTS quote_url       varchar(1000),
  ADD COLUMN IF NOT EXISTS quote_review_id varchar(32);
