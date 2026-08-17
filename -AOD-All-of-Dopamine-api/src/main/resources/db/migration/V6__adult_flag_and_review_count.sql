-- ============================================================================
-- V6: Steam 정제 — 성인(성적 콘텐츠) 플래그 승격 + 리뷰 총수 도메인 승격 (2026-08)
-- 필터 규율: platform_data.attributes(JSONB)는 필터 축 금지라 매 쿼리 attr 파싱 대신
-- contents/game_contents 컬럼으로 승격한다 (docs/plans/2026-08-18-steam-refinements ①④).
--
-- 성인 판정 기준: Steam content_descriptors.ids에 3(Adult Only Sexual Content) 또는
--   4(Frequent Nudity or Sexual Content) 포함 = 성적 콘텐츠만 제외.
--   required_age>=18 기준은 기각 — 로컬 실측 10건 전부 폭력계 클래식(GTA류 부류)으로
--   정반대로 작동했다. 폭력성 18금은 노출 유지가 정책.
-- 과거 수집분엔 content_descriptor_ids가 없어 여기서 성인 백필 불가 —
--   재크롤로 attr이 채워지면 부팅 reconcile(SteamRefinementReconciler)이 자동 플래그.
-- 전부 IF NOT EXISTS + 조건 가드로 멱등.
-- ============================================================================

-- §1. contents.is_adult — 성적 콘텐츠 목록 노출 차단 플래그
--     (상세 직접 접근은 허용 — 목록·검색·신작·발견 쿼리에서만 is_adult=false 필터)
ALTER TABLE contents ADD COLUMN IF NOT EXISTS is_adult boolean NOT NULL DEFAULT false;

-- §2. 구 기준(required_age>=18) 백필 철회 — 구 V6가 이미 실행된 DB(로컬)의 원복.
--     성적 디스크립터(3/4) 근거가 없는 true만 false로 되돌린다
--     (descriptor 기반 true는 보존 — 신규 DB에선 no-op, 재실행에도 멱등).
UPDATE contents c
   SET is_adult = false
 WHERE c.is_adult = true
   AND NOT EXISTS (
       SELECT 1 FROM platform_data pd
        WHERE pd.content_id = c.content_id
          AND pd.platform_name = 'Steam'
          AND ((pd.attributes -> 'content_descriptor_ids') @> '3'::jsonb
               OR (pd.attributes -> 'content_descriptor_ids') @> '4'::jsonb));

-- is_adult 인덱스는 만들지 않는다: true가 극소수로 필터 방향(is_adult=false)이
-- 거의 전 행에 매치되는 저선택도 조건이라 부분 인덱스를 만들어도 플래너가
-- 선택하지 않는다 — 기존 인덱스 경로 유지.

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
