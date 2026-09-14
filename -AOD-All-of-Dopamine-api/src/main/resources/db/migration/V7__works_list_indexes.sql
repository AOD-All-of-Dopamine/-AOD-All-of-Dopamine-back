-- ============================================================================
-- V7: 탐색 목록 인덱스 2종 (troubleshooting/07 §8 ②, 2026-09)
--
-- 배경: #116 동적 조립으로 `IS NULL OR` 스위치를 제거한 뒤에도 E2E가 느슨한 필터
-- (reviewCountMin=100)에서 1.45s로 평평했다. 플랜은 contents 전 도메인 Seq Scan(28K 페이지)
-- + Parallel Hash Join ×2(본·count) — ORDER BY release_date 를 지원하는 인덱스가 없어
-- 매 요청 후보 전체를 읽고 정렬해야 하기 때문. game_contents 쪽도 review_count 인덱스가 없어
-- 18만 행 Seq Scan(4.8K 페이지).
--
-- 두 인덱스 모두 IF NOT EXISTS 로 멱등. 비동시(non-CONCURRENTLY) 생성 — Flyway 트랜잭션 안에서
-- 실행되며 V1 GIN 인덱스와 같은 방식. 생성 중 해당 테이블 쓰기(크롤러)만 수 초 대기, 읽기는 영향 없음.
-- ============================================================================

-- §1. contents 최신순 목록 인덱스 — 모든 목록 경로의 ORDER BY (release_date DESC NULLS LAST, content_id ASC)
--     와 컬럼·방향·NULL 순서가 정확히 일치해야 정렬 없이 인덱스 순서를 그대로 쓴다.
--     domain 등치 조건이 선두 → 도메인 탭 안에서 최신순 스캔 + LIMIT 조기 종료.
--     부분 인덱스(is_adult = false): 목록 경로는 전부 성인 제외라 인덱스가 그 조건을 내포.
--     효과: (a) 필터 없는 도메인 목록(findByDomainOrderByReleaseDateDesc)의 전체 정렬 제거
--          (b) 동적 조립 필터 목록에서 "최신순으로 걷다 20건 차면 중단" 플랜 선택 가능
--     (count 쿼리는 조기 종료가 없어 이 인덱스로 안 빨라진다 — §8 ③ Slice/count 분리 과제)
CREATE INDEX IF NOT EXISTS idx_contents_domain_release
    ON contents (domain, release_date DESC NULLS LAST, content_id ASC)
    WHERE is_adult = false;

-- §2. game_contents 리뷰 총수 인덱스 — reviewCountMin 축 (review_count >= :min)
--     INCLUDE (content_id): 조인 키까지 인덱스에 담아 index-only scan 허용
--     (18만 행 Seq Scan 4.8K 페이지 → 조건 구간 인덱스 페이지 수십~수백 개).
--     부분 인덱스(review_count IS NOT NULL): null = 미수집이며 >= 비교에서 어차피 제외.
CREATE INDEX IF NOT EXISTS idx_game_contents_review_count
    ON game_contents (review_count)
    INCLUDE (content_id)
    WHERE review_count IS NOT NULL;
