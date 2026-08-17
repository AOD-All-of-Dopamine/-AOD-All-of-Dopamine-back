-- ============================================================================
-- V5: genres/platforms 승격 백필 (2026-07 승격의 수동 SQL을 Flyway로 이관)
-- 원본: docs/sql/2026-07-promote-{genres,platforms}-to-contents.sql §1~§3
-- (§4 DROP은 검증 후 별도 마이그레이션으로 — 여기 포함하지 않음)
-- 전부 IF NOT EXISTS + null 가드로 멱등. 백필이 이미 된 DB(로컬)에선 no-op.
-- ============================================================================

-- §1. 컬럼 생성
ALTER TABLE contents ADD COLUMN IF NOT EXISTS genres text[];
ALTER TABLE contents ADD COLUMN IF NOT EXISTS platforms text[];

-- §2-a. genres 백필: 도메인 테이블 → contents (값 있는 행은 보존)
UPDATE contents c SET genres = d.genres
  FROM movie_contents d
 WHERE c.content_id = d.content_id AND c.genres IS NULL AND d.genres IS NOT NULL;

UPDATE contents c SET genres = d.genres
  FROM tv_contents d
 WHERE c.content_id = d.content_id AND c.genres IS NULL AND d.genres IS NOT NULL;

UPDATE contents c SET genres = d.genres
  FROM game_contents d
 WHERE c.content_id = d.content_id AND c.genres IS NULL AND d.genres IS NOT NULL;

UPDATE contents c SET genres = d.genres
  FROM webtoon_contents d
 WHERE c.content_id = d.content_id AND c.genres IS NULL AND d.genres IS NOT NULL;

UPDATE contents c SET genres = d.genres
  FROM webnovel_contents d
 WHERE c.content_id = d.content_id AND c.genres IS NULL AND d.genres IS NOT NULL;

-- §2-b. platforms 백필: 도메인 테이블 → contents
UPDATE contents c SET platforms = d.platforms
  FROM movie_contents d
 WHERE c.content_id = d.content_id AND c.platforms IS NULL AND d.platforms IS NOT NULL;

UPDATE contents c SET platforms = d.platforms
  FROM tv_contents d
 WHERE c.content_id = d.content_id AND c.platforms IS NULL AND d.platforms IS NOT NULL;

UPDATE contents c SET platforms = d.platforms
  FROM game_contents d
 WHERE c.content_id = d.content_id AND c.platforms IS NULL AND d.platforms IS NOT NULL;

UPDATE contents c SET platforms = d.platforms
  FROM webtoon_contents d
 WHERE c.content_id = d.content_id AND c.platforms IS NULL AND d.platforms IS NOT NULL;

UPDATE contents c SET platforms = d.platforms
  FROM webnovel_contents d
 WHERE c.content_id = d.content_id AND c.platforms IS NULL AND d.platforms IS NOT NULL;

-- §2-c. 보정: platform_data가 진실 — 배열에 없는 platform_name을 합쳐 넣는다
--        (과거 platforms 배열 교체 버그로 유실된 플랫폼 복구)
UPDATE contents c
   SET platforms = (
       SELECT ARRAY(SELECT DISTINCT p FROM UNNEST(COALESCE(c.platforms, '{}') ||
              ARRAY(SELECT pd.platform_name FROM platform_data pd
                     WHERE pd.content_id = c.content_id)) AS p)
   )
 WHERE EXISTS (SELECT 1 FROM platform_data pd
                WHERE pd.content_id = c.content_id
                  AND NOT (pd.platform_name = ANY(COALESCE(c.platforms, '{}'))));

-- §3. GIN 인덱스
CREATE INDEX IF NOT EXISTS idx_contents_genres    ON contents USING GIN (genres);
CREATE INDEX IF NOT EXISTS idx_contents_platforms ON contents USING GIN (platforms);
