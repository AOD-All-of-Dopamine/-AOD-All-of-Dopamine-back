-- =====================================
-- 도메인 테이블에 platforms 컬럼 추가
-- =====================================

-- Step 1: 모든 도메인 테이블에 platforms 컬럼 추가
ALTER TABLE movie_contents 
ADD COLUMN IF NOT EXISTS platforms text[];

ALTER TABLE tv_contents 
ADD COLUMN IF NOT EXISTS platforms text[];

ALTER TABLE game_contents 
ADD COLUMN IF NOT EXISTS platforms text[];

ALTER TABLE webtoon_contents 
ADD COLUMN IF NOT EXISTS platforms text[];

ALTER TABLE webnovel_contents 
ADD COLUMN IF NOT EXISTS platforms text[];

-- Step 2: 데이터 마이그레이션 (platform_data에서 복사)

-- 2-1. movie_contents: TMDB + watch_providers 통합
UPDATE movie_contents mc
SET platforms = (
    SELECT ARRAY_AGG(DISTINCT platform_name ORDER BY platform_name)
    FROM (
        -- TMDB_MOVIE (데이터 소스)
        SELECT pd.platform_name
        FROM platform_data pd
        WHERE pd.content_id = mc.content_id
          AND pd.platform_name = 'TMDB_MOVIE'
        
        UNION
        
        -- watch_providers (OTT 플랫폼들)
        SELECT jsonb_array_elements_text(pd.attributes->'watch_providers') as platform_name
        FROM platform_data pd
        WHERE pd.content_id = mc.content_id
          AND pd.platform_name = 'TMDB_MOVIE'
          AND pd.attributes->'watch_providers' IS NOT NULL
          AND jsonb_typeof(pd.attributes->'watch_providers') = 'array'
    ) AS all_platforms
)
WHERE EXISTS (
    SELECT 1 FROM platform_data pd 
    WHERE pd.content_id = mc.content_id
);

-- 2-2. tv_contents: TMDB + watch_providers 통합
UPDATE tv_contents tc
SET platforms = (
    SELECT ARRAY_AGG(DISTINCT platform_name ORDER BY platform_name)
    FROM (
        SELECT pd.platform_name
        FROM platform_data pd
        WHERE pd.content_id = tc.content_id
          AND pd.platform_name = 'TMDB_TV'
        
        UNION
        
        SELECT jsonb_array_elements_text(pd.attributes->'watch_providers') as platform_name
        FROM platform_data pd
        WHERE pd.content_id = tc.content_id
          AND pd.platform_name = 'TMDB_TV'
          AND pd.attributes->'watch_providers' IS NOT NULL
          AND jsonb_typeof(pd.attributes->'watch_providers') = 'array'
    ) AS all_platforms
)
WHERE EXISTS (
    SELECT 1 FROM platform_data pd 
    WHERE pd.content_id = tc.content_id
);

-- 2-3. game_contents: Steam 등
UPDATE game_contents gc
SET platforms = (
    SELECT ARRAY_AGG(DISTINCT pd.platform_name ORDER BY pd.platform_name)
    FROM platform_data pd
    WHERE pd.content_id = gc.content_id
)
WHERE EXISTS (
    SELECT 1 FROM platform_data pd 
    WHERE pd.content_id = gc.content_id
);

-- 2-4. webtoon_contents: NaverWebtoon 등
UPDATE webtoon_contents wc
SET platforms = (
    SELECT ARRAY_AGG(DISTINCT pd.platform_name ORDER BY pd.platform_name)
    FROM platform_data pd
    WHERE pd.content_id = wc.content_id
)
WHERE EXISTS (
    SELECT 1 FROM platform_data pd 
    WHERE pd.content_id = wc.content_id
);

-- 2-5. webnovel_contents: NaverSeries, KakaoPage 등
UPDATE webnovel_contents wnc
SET platforms = (
    SELECT ARRAY_AGG(DISTINCT pd.platform_name ORDER BY pd.platform_name)
    FROM platform_data pd
    WHERE pd.content_id = wnc.content_id
)
WHERE EXISTS (
    SELECT 1 FROM platform_data pd 
    WHERE pd.content_id = wnc.content_id
);

-- Step 3: GIN 인덱스 생성 (genres와 동일한 패턴)
CREATE INDEX IF NOT EXISTS idx_movie_platforms 
ON movie_contents USING GIN (platforms);

CREATE INDEX IF NOT EXISTS idx_tv_platforms 
ON tv_contents USING GIN (platforms);

CREATE INDEX IF NOT EXISTS idx_game_platforms 
ON game_contents USING GIN (platforms);

CREATE INDEX IF NOT EXISTS idx_webtoon_platforms 
ON webtoon_contents USING GIN (platforms);

CREATE INDEX IF NOT EXISTS idx_webnovel_platforms 
ON webnovel_contents USING GIN (platforms);
