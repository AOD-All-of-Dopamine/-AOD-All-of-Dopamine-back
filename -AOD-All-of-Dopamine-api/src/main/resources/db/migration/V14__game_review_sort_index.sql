-- ============================================================================
-- V14: 탐색 게임 탭 "리뷰 많은 순" 인덱스 (트렌드 · 탐색 설계 2026-10-04, PR 검수 반영)
--
-- 게임 탭 기본 정렬이 sortBy=steamReviews 가 되면서 WorksQueryBuilder 가
--   contents c JOIN game_contents gs ... ORDER BY gs.review_count DESC NULLS LAST, c.content_id
-- 를 만든다. V7 의 idx_game_contents_review_count 는 오름차순 · 부분 인덱스(review_count IS NOT NULL)라
-- 이 순서를 줄 수 없어 매 요청 게임 18만 행을 읽고 정렬한다(V7 이 없애려던 그 플랜).
-- 이 인덱스로 game_contents 를 리뷰 순으로 걷고 contents 를 PK 로 찾다가 LIMIT 에서 멈춘다.
-- 로컬 같은 규모(contents 40만 · 게임 18만) 실측: 첫 쪽 49ms(LEFT JOIN · 전체 정렬) → 0.25ms.
-- count 쿼리는 이전(최신순)과 같다.
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_game_contents_review_desc
    ON game_contents (review_count DESC NULLS LAST, content_id);
