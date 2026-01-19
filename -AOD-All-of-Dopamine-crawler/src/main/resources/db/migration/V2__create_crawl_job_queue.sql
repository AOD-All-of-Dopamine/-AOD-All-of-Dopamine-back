-- 크롤링 작업 큐 테이블 생성
-- Job Queue 패턴을 위한 작업 관리 테이블

CREATE TABLE IF NOT EXISTS crawl_job_queue (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority INTEGER NOT NULL DEFAULT 5,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    
    -- 유니크 제약: 같은 타입의 같은 대상은 중복 등록 방지
    CONSTRAINT uk_job_type_target UNIQUE (job_type, target_id)
);

-- 인덱스: 상태와 우선순위 기준 조회 최적화
CREATE INDEX IF NOT EXISTS idx_job_status_priority ON crawl_job_queue(status, priority DESC);

-- 인덱스: 타입별 상태 조회 최적화
CREATE INDEX IF NOT EXISTS idx_job_type_status ON crawl_job_queue(job_type, status);

-- 인덱스: 생성 시간 기준 조회
CREATE INDEX IF NOT EXISTS idx_job_created_at ON crawl_job_queue(created_at);

-- 코멘트
COMMENT ON TABLE crawl_job_queue IS '크롤링 작업 큐 - Producer-Consumer 패턴';
COMMENT ON COLUMN crawl_job_queue.job_type IS '작업 타입: STEAM_GAME, TMDB_MOVIE, TMDB_TV, NAVER_WEBTOON, etc.';
COMMENT ON COLUMN crawl_job_queue.target_id IS '크롤링 대상 ID (Steam appId, TMDB movieId, 웹툰 titleId 등)';
COMMENT ON COLUMN crawl_job_queue.status IS '작업 상태: PENDING, PROCESSING, COMPLETED, RETRY, FAILED, SKIPPED';
COMMENT ON COLUMN crawl_job_queue.priority IS '우선순위 (낮을수록 높음): 1~10';
COMMENT ON COLUMN crawl_job_queue.retry_count IS '재시도 횟수 (최대 3회)';
