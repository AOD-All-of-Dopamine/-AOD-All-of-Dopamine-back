-- ============================================================================
-- V8: 추천 서빙 상태(aod_rec) + 행동 로그(aod_log) 스키마 (2026-09)
-- 근거: AI 레포 recommendation/REC_TAB_DESIGN.md §5-3.
-- 서빙 상태는 추천 정확성에 필요해 동기로 쓰고(파티션 없음), 로그는 유실돼도 추천이
-- 틀리지 않는 경로라 비동기·월 파티션이다. 두 스키마를 섞지 않는다.
-- 전부 IF NOT EXISTS 로 멱등. 로그 테이블에는 FK 를 걸지 않는다(탈퇴·삭제와 독립).
-- 이후 달의 파티션은 PartitionMaintenanceJob 이 만든다.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS aod_rec;
CREATE SCHEMA IF NOT EXISTS aod_log;

-- ---------- aod_rec: 서빙 상태 ----------
CREATE TABLE IF NOT EXISTS aod_rec.rec_chain (
    chain_id   uuid        PRIMARY KEY,
    user_id    bigint      NOT NULL,
    tab        text        NOT NULL,
    seen_ids   bigint[]    NOT NULL DEFAULT '{}',   -- content_id, 최대 500
    page_depth int         NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rec_chain_updated ON aod_rec.rec_chain (updated_at);

CREATE TABLE IF NOT EXISTS aod_rec.not_interested (
    user_id    bigint      NOT NULL,
    content_id bigint      NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, content_id)
);
CREATE INDEX IF NOT EXISTS idx_not_interested_created ON aod_rec.not_interested (created_at);

CREATE TABLE IF NOT EXISTS aod_rec.corpus_map (
    content_id     bigint NOT NULL,
    platform       text   NOT NULL,
    corpus_key     text   NOT NULL,
    corpus_version text   NOT NULL,
    PRIMARY KEY (platform, corpus_key, corpus_version)
);
CREATE INDEX IF NOT EXISTS idx_corpus_map_content ON aod_rec.corpus_map (content_id, corpus_version);

-- ---------- aod_log: 로그 (월 파티션 · PK 는 파티션 키 포함) ----------
CREATE TABLE IF NOT EXISTS aod_log.rec_request (
    request_id       uuid        NOT NULL,
    served_at        timestamptz NOT NULL,
    chain_id         uuid        NOT NULL,
    page_depth       int         NOT NULL,
    user_id          bigint,
    anon_id          uuid        NOT NULL,
    session_id       uuid        NOT NULL,
    surface          text        NOT NULL,
    tab              text        NOT NULL,
    seed_ids         bigint[]    NOT NULL,
    seed_sources     text[]      NOT NULL,
    disliked_ids     bigint[]    NOT NULL,
    excluded_ids     bigint[]    NOT NULL,
    seen_ids         bigint[]    NOT NULL,
    dropped_seed_ids bigint[]    NOT NULL DEFAULT '{}',
    experiments      jsonb       NOT NULL DEFAULT '{}',
    versions         jsonb       NOT NULL,
    fallback         boolean     NOT NULL,
    fallback_reason  text,
    partial          text[]      NOT NULL DEFAULT '{}',
    latency_ms       int,
    app_version      text,
    device           text,
    PRIMARY KEY (request_id, served_at)
) PARTITION BY RANGE (served_at);

CREATE TABLE IF NOT EXISTS aod_log.rec_item_served (
    impression_id    uuid        NOT NULL,
    served_at        timestamptz NOT NULL,
    request_id       uuid        NOT NULL,
    content_id       bigint      NOT NULL,
    platform         text        NOT NULL,
    corpus_key       text        NOT NULL,
    rank_position    int         NOT NULL,
    candidate_source text        NOT NULL,
    reason_type      text,
    reason_seed_id   bigint,
    score            jsonb       NOT NULL,
    factor_schema    text,
    is_exploration   boolean     NOT NULL DEFAULT false,
    propensity       real        NOT NULL DEFAULT 1.0,
    interleave_team  text,
    is_served        boolean     NOT NULL DEFAULT true,
    dropped_reason   text,
    PRIMARY KEY (impression_id, served_at)
) PARTITION BY RANGE (served_at);

CREATE TABLE IF NOT EXISTS aod_log.event (
    event_id      uuid        NOT NULL,
    server_ts     timestamptz NOT NULL,
    event_type    text        NOT NULL,
    origin        text        NOT NULL,             -- client / server
    user_id       bigint,
    anon_id       uuid,
    session_id    uuid,
    content_id    bigint,
    request_id    uuid,
    impression_id uuid,
    surface       text,
    payload       jsonb       NOT NULL DEFAULT '{}',
    client_ts     timestamptz,
    app_version   text,
    device        text,
    PRIMARY KEY (event_id, server_ts)
) PARTITION BY RANGE (server_ts);

-- user_agent 는 봇 판별용으로 90일만 둔다 → 세션 단위 별도 테이블 + 파티션 DROP
CREATE TABLE IF NOT EXISTS aod_log.client_agent (
    session_id uuid        NOT NULL,
    first_seen timestamptz NOT NULL,
    user_agent text        NOT NULL,
    PRIMARY KEY (session_id, first_seen)
) PARTITION BY RANGE (first_seen);

-- 파티션 PK 로는 event_id 전역 유일을 보장할 수 없다 → 짧은 보관 테이블로 거른다 (7일)
CREATE TABLE IF NOT EXISTS aod_log.event_seen (
    event_id  uuid        PRIMARY KEY,
    server_ts timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_event_seen_ts ON aod_log.event_seen (server_ts);

-- 거절 이벤트 1% 표본 (30일)
CREATE TABLE IF NOT EXISTS aod_log.rejected_event (
    id        bigserial   PRIMARY KEY,
    raw       jsonb       NOT NULL,
    reason    text        NOT NULL,
    server_ts timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_rejected_event_ts ON aod_log.rejected_event (server_ts);

-- DEFAULT 파티션은 파티션 테이블 전부에. 하나라도 빠지면 월 파티션이 밀린 순간 그 테이블만 INSERT 가 실패한다.
CREATE TABLE IF NOT EXISTS aod_log.rec_request_default     PARTITION OF aod_log.rec_request     DEFAULT;
CREATE TABLE IF NOT EXISTS aod_log.rec_item_served_default PARTITION OF aod_log.rec_item_served DEFAULT;
CREATE TABLE IF NOT EXISTS aod_log.event_default           PARTITION OF aod_log.event           DEFAULT;
CREATE TABLE IF NOT EXISTS aod_log.client_agent_default    PARTITION OF aod_log.client_agent    DEFAULT;

-- 이번 달·다음 달 파티션. 이름 규칙 {table}_yYYYYmMM 은 PartitionNames 와 같아야 한다.
-- 경계는 UTC 로 못박는다 (예: '2026-10-01T00:00:00Z').
DO $$
DECLARE
    t     text;
    m     date;
    pname text;
BEGIN
    FOREACH t IN ARRAY ARRAY['rec_request', 'rec_item_served', 'event', 'client_agent'] LOOP
        FOR i IN 0..1 LOOP
            m := (date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => i))::date;
            pname := t || '_y' || to_char(m, 'YYYY') || 'm' || to_char(m, 'MM');
            EXECUTE format(
                'CREATE TABLE IF NOT EXISTS aod_log.%I PARTITION OF aod_log.%I FOR VALUES FROM (%L) TO (%L)',
                pname, t,
                to_char(m, 'YYYY-MM-DD') || 'T00:00:00Z',
                to_char((m + interval '1 month')::date, 'YYYY-MM-DD') || 'T00:00:00Z');
        END LOOP;
    END LOOP;
END
$$;

-- 인덱스 (부모에 만들면 기존·이후 파티션에 전파된다)
CREATE INDEX IF NOT EXISTS idx_rec_request_user_served   ON aod_log.rec_request (user_id, served_at);
CREATE INDEX IF NOT EXISTS idx_rec_request_chain         ON aod_log.rec_request (chain_id);
CREATE INDEX IF NOT EXISTS idx_rec_item_served_request   ON aod_log.rec_item_served (request_id);
CREATE INDEX IF NOT EXISTS idx_event_impression          ON aod_log.event (impression_id);
CREATE INDEX IF NOT EXISTS idx_event_user_ts             ON aod_log.event (user_id, server_ts);
CREATE INDEX IF NOT EXISTS idx_event_session             ON aod_log.event (session_id);
CREATE INDEX IF NOT EXISTS idx_client_agent_session      ON aod_log.client_agent (session_id);
