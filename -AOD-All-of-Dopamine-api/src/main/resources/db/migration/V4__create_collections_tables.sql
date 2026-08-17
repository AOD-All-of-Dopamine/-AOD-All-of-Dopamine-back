-- ============================================================================
-- V4: 컬렉션 기능 테이블 (2026-08)
-- prod는 ddl-auto가 없으므로 이 마이그레이션이 스키마의 단일 출처.
-- 로컬(ddl-auto=update)에선 이미 생성돼 있을 수 있어 전부 IF NOT EXISTS — 멱등.
-- DDL은 api 모듈 엔티티(Collection/CollectionItem/CollectionLike)와 1:1 대응.
-- ============================================================================

CREATE TABLE IF NOT EXISTS collections (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    title       VARCHAR(60)  NOT NULL,
    description VARCHAR(300),
    domain      VARCHAR(16)  NOT NULL,
    tint        VARCHAR(16)  NOT NULL DEFAULT 'PINE',
    visibility  VARCHAR(10)  NOT NULL DEFAULT 'PUBLIC',
    like_count  INTEGER      NOT NULL DEFAULT 0,
    view_count  BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_collections_visibility_domain
    ON collections (visibility, domain);
CREATE INDEX IF NOT EXISTS idx_collections_user
    ON collections (user_id);

CREATE TABLE IF NOT EXISTS collection_items (
    id            BIGSERIAL PRIMARY KEY,
    collection_id BIGINT       NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    content_id    BIGINT       NOT NULL REFERENCES contents (content_id),
    comment       VARCHAR(200),
    position      INTEGER      NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT uk_collection_items_collection_content UNIQUE (collection_id, content_id)
);

CREATE TABLE IF NOT EXISTS collection_likes (
    id            BIGSERIAL PRIMARY KEY,
    collection_id BIGINT    NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    user_id       BIGINT    NOT NULL REFERENCES users (id),
    created_at    TIMESTAMP NOT NULL,
    CONSTRAINT uk_collection_likes_collection_user UNIQUE (collection_id, user_id)
);
