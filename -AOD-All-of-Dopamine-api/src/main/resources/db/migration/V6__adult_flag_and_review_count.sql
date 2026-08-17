-- ============================================================================
-- V6: Steam 정제 — 성인 플래그 마스터 승격 + 리뷰 총수 도메인 승격 (2026-08)
-- 필터 규율: platform_data.attributes(JSONB)는 필터 축 금지라 매 쿼리 attr 파싱 대신
-- contents/game_contents 컬럼으로 승격한다 (docs/plans/2026-08-18-steam-refinements ①④).
-- 전부 IF NOT EXISTS + 조건 가드로 멱등. 이미 적용된 DB에선 no-op.
-- ============================================================================

-- §1. contents.is_adult — 성인 콘텐츠 목록 노출 차단 플래그
--     (상세 직접 접근은 허용 — 목록·검색·신작·발견 쿼리에서만 is_adult=false 필터)
ALTER TABLE contents ADD COLUMN IF NOT EXISTS is_adult boolean NOT NULL DEFAULT false;

-- §2. Steam required_age >= 18 백필 (platform_data.attributes 기준 — 기존 수집분 노출 차단)
--     required_age는 숫자(0)·문자열("18") 혼재 저장 — 숫자 형태만 캐스팅해 비교.
UPDATE contents c
   SET is_adult = true
  FROM platform_data pd
 WHERE pd.content_id = c.content_id
   AND pd.platform_name = 'Steam'
   AND (pd.attributes ->> 'required_age') ~ '^[0-9]+$'
   AND (pd.attributes ->> 'required_age')::int >= 18
   AND c.is_adult = false;

-- is_adult 인덱스는 만들지 않는다: true가 전체의 ~1%(로컬 894건 중 10건)로
-- 필터 방향(is_adult=false)이 거의 전 행에 매치되는 저선택도 조건이라
-- 부분 인덱스를 만들어도 플래너가 선택하지 않는다 — 기존 인덱스 경로 유지.

-- §3. game_contents.review_count — Steam 리뷰 총수 (reviewCountMin 필터 축)
--     null = 미수집 (재크롤 시 yml 매핑 review_summary.total_reviews → domain.reviewCount로 채워짐)
ALTER TABLE game_contents ADD COLUMN IF NOT EXISTS review_count integer;

-- §4. 기존 수집분 백필: attributes.review_summary.total_reviews → review_count
UPDATE game_contents g
   SET review_count = (pd.attributes -> 'review_summary' ->> 'total_reviews')::int
  FROM platform_data pd
 WHERE pd.content_id = g.content_id
   AND pd.platform_name = 'Steam'
   AND (pd.attributes -> 'review_summary' ->> 'total_reviews') ~ '^[0-9]+$'
   AND g.review_count IS NULL;
