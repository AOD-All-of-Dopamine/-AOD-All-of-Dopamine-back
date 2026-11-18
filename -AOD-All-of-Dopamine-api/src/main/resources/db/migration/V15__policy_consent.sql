-- 가입 때 동의한 처리방침 버전과 시각 — 동의를 받았다는 기록.
-- users 는 Flyway 이전에 만들어진 테이블이라 ALTER 만 한다. 멱등.
ALTER TABLE users ADD COLUMN IF NOT EXISTS policy_version   varchar(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS policy_agreed_at timestamp;

-- 기존 가입자는 동의한 것으로 둔다(2026-11-18 결정 — 실사용자가 없다).
UPDATE users SET policy_version = '2026-11-18', policy_agreed_at = now() WHERE policy_version IS NULL;
