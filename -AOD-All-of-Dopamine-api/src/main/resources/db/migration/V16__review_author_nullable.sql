-- 탈퇴한 회원의 리뷰는 남기고 작성자만 비운다 → user_id 를 비울 수 있어야 한다.
-- reviews 는 Flyway 이전에 만들어진 테이블이라 ALTER 만 한다. 멱등.
ALTER TABLE reviews ALTER COLUMN user_id DROP NOT NULL;
