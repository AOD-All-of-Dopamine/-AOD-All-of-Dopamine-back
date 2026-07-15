-- ============================================================================
-- platforms 승격 마이그레이션: 도메인 테이블 → contents (2026-07)
-- genres 승격(2026-07-promote-genres-to-contents.sql)과 동일 패턴 — 함께 실행 권장.
--
-- 실행 시점: 승격 코드 배포 "전"에 실행 권장 (실행 후 배포 순서 자유).
-- 멱등: IF NOT EXISTS + null 가드로 여러 번 실행해도 무해.
-- ============================================================================

-- §1. 컬럼 생성
ALTER TABLE contents ADD COLUMN IF NOT EXISTS platforms text[];

-- §2. 백필: 도메인 테이블 → contents (이미 값이 있는 행은 건드리지 않음)
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

-- §2-보정. 과거 platforms 배열 교체 버그(2026-07 수정 전)로 유실된 플랫폼 복구:
--   platform_data가 진실이므로, 거기 있는 platform_name이 배열에 없으면 합쳐 넣는다.
UPDATE contents c
   SET platforms = (
       SELECT ARRAY(SELECT DISTINCT p FROM UNNEST(COALESCE(c.platforms, '{}') ||
              ARRAY(SELECT pd.platform_name FROM platform_data pd
                     WHERE pd.content_id = c.content_id)) AS p)
   )
 WHERE EXISTS (SELECT 1 FROM platform_data pd
                WHERE pd.content_id = c.content_id
                  AND NOT (pd.platform_name = ANY(COALESCE(c.platforms, '{}'))));

-- §3. GIN 인덱스 (크롤러의 DatabaseIndexInitializer도 부팅 시 생성하지만 선실행 무해)
CREATE INDEX IF NOT EXISTS idx_contents_platforms ON contents USING GIN (platforms);

-- 검증 쿼리:
--   SELECT domain, COUNT(*) FILTER (WHERE platforms IS NOT NULL) AS with_platforms, COUNT(*) AS total
--     FROM contents GROUP BY domain;

-- ============================================================================
-- §4. 사후 정리 (⚠️ 새 코드 배포 + 검증이 끝난 뒤에만 실행)
-- ============================================================================
-- DROP INDEX IF EXISTS idx_movie_platforms;
-- DROP INDEX IF EXISTS idx_tv_platforms;
-- DROP INDEX IF EXISTS idx_game_platforms;
-- DROP INDEX IF EXISTS idx_webtoon_platforms;
-- DROP INDEX IF EXISTS idx_webnovel_platforms;
-- ALTER TABLE movie_contents    DROP COLUMN IF EXISTS platforms;
-- ALTER TABLE tv_contents      DROP COLUMN IF EXISTS platforms;
-- ALTER TABLE game_contents    DROP COLUMN IF EXISTS platforms;
-- ALTER TABLE webtoon_contents DROP COLUMN IF EXISTS platforms;
-- ALTER TABLE webnovel_contents DROP COLUMN IF EXISTS platforms;
