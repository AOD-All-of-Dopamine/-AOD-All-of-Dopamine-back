-- 장르 필터링 성능 최적화를 위한 GIN 인덱스 생성
-- PostgreSQL JSONB 컬럼에 대한 인덱스
-- ?& 연산자 (contains all) 성능 향상

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
