-- 장르 필터링 성능 최적화를 위한 GIN 인덱스 생성
-- PostgreSQL text[] 배열 컬럼에 대한 인덱스
-- @> (contains) 연산자 성능 향상

-- 영화 장르 인덱스
CREATE INDEX IF NOT EXISTS idx_movie_genres ON movie_contents USING GIN (genres);

-- TV 장르 인덱스
CREATE INDEX IF NOT EXISTS idx_tv_genres ON tv_contents USING GIN (genres);

-- 게임 장르 인덱스
CREATE INDEX IF NOT EXISTS idx_game_genres ON game_contents USING GIN (genres);

-- 웹툰 장르 인덱스
CREATE INDEX IF NOT EXISTS idx_webtoon_genres ON webtoon_contents USING GIN (genres);

-- 웹소설 장르 인덱스
CREATE INDEX IF NOT EXISTS idx_webnovel_genres ON webnovel_contents USING GIN (genres);

-- 인덱스 생성 확인
SELECT 
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname LIKE 'idx_%_genres'
ORDER BY tablename;

-- 인덱스 사용 통계 확인 (실행 후 일정 시간 후 확인)
-- SELECT 
--     schemaname,
--     tablename,
--     indexname,
--     idx_scan as index_scans,
--     idx_tup_read as tuples_read,
--     idx_tup_fetch as tuples_fetched
-- FROM pg_stat_user_indexes
-- WHERE indexname LIKE 'idx_%_genres'
-- ORDER BY idx_scan DESC;
