-- ============================================================================
-- genres 승격 마이그레이션: 도메인 테이블 → contents (2026-07)
--
-- 실행 시점: 승격 코드 배포 "전"에 실행 권장 (실행 후 배포 순서 자유).
--   * 컬럼 자체는 ddl-auto=update가 만들어주지만, 백필은 이 SQL이 해야 함.
--   * 배포 후 실행해도 무해 (IF NOT EXISTS + null 가드로 멱등).
-- 실행 방법: psql -f 또는 DB 클라이언트에서 §1~§3 실행. §4는 검증 후 별도 실행.
-- ============================================================================

-- §1. 컬럼 생성 (ddl-auto보다 먼저 실행해도 되도록)
ALTER TABLE contents ADD COLUMN IF NOT EXISTS genres text[];

-- §2. 백필: 도메인 테이블 → contents (이미 값이 있는 행은 건드리지 않음 — 멱등)
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

-- §3. GIN 인덱스 (크롤러의 DatabaseIndexInitializer도 부팅 시 생성하지만 선실행 무해)
CREATE INDEX IF NOT EXISTS idx_contents_genres ON contents USING GIN (genres);

-- 검증 쿼리 (백필 결과 확인용):
--   SELECT domain, COUNT(*) FILTER (WHERE genres IS NOT NULL) AS with_genres, COUNT(*) AS total
--     FROM contents GROUP BY domain;

-- ============================================================================
-- §4. 사후 정리 (⚠️ 새 코드 배포 + 백필 검증이 끝난 뒤에만 실행할 것)
--     구 도메인 테이블의 genres 컬럼/인덱스 제거. ddl-auto=update는 컬럼을 지우지
--     않으므로 이 SQL 전까지는 무해하게 남아있음 (롤백 안전판).
-- ============================================================================
-- DROP INDEX IF EXISTS idx_movie_genres;
-- DROP INDEX IF EXISTS idx_tv_genres;
-- DROP INDEX IF EXISTS idx_game_genres;
-- DROP INDEX IF EXISTS idx_webtoon_genres;
-- DROP INDEX IF EXISTS idx_webnovel_genres;
-- ALTER TABLE movie_contents    DROP COLUMN IF EXISTS genres;
-- ALTER TABLE tv_contents      DROP COLUMN IF EXISTS genres;
-- ALTER TABLE game_contents    DROP COLUMN IF EXISTS genres;
-- ALTER TABLE webtoon_contents DROP COLUMN IF EXISTS genres;
-- ALTER TABLE webnovel_contents DROP COLUMN IF EXISTS genres;
