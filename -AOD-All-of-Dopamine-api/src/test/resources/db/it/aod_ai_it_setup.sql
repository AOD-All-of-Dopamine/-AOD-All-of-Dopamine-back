CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS aod_ai;

CREATE TABLE aod_ai.content_embedding (
  content_id bigint PRIMARY KEY, embedding vector(1024) NOT NULL, model text NOT NULL, dim int NOT NULL DEFAULT 1024
);
CREATE TABLE aod_ai.content_fun_tag (
  content_id bigint NOT NULL, tag text NOT NULL, tag_score real NOT NULL, tag_confidence real NOT NULL,
  PRIMARY KEY (content_id, tag)
);
CREATE TABLE aod_ai.content_semantic_profile (content_id bigint PRIMARY KEY, domain text, profile_text text, content_hash text);
CREATE TABLE aod_ai.content_quality_score (
  content_id bigint PRIMARY KEY, bayesian_score real, platform_rank_score real, review_count_score real,
  recency_score real, quality_popularity_score real, computed_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE aod_ai.fun_tag_dict (id bigserial PRIMARY KEY, name text NOT NULL UNIQUE);
CREATE TABLE aod_ai.user_profile_cache (user_id bigint PRIMARY KEY, positive_count int NOT NULL DEFAULT 0);
CREATE TABLE aod_ai.rec_impression (
  id bigserial PRIMARY KEY, request_id uuid NOT NULL, user_id bigint, location text NOT NULL,
  selected_content_id bigint, content_id bigint NOT NULL, candidate_source text, rank_position int,
  score_breakdown jsonb, served_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE aod_ai.rec_event (
  id bigserial PRIMARY KEY, request_id uuid, user_id bigint, content_id bigint NOT NULL,
  event_type text NOT NULL, value real, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.contents (
  content_id bigint PRIMARY KEY, domain varchar(50), master_title varchar(500), release_date date
);

INSERT INTO public.contents(content_id, domain, master_title, release_date) VALUES
  (1,'MOVIE','A', DATE '2026-07-01'), (2,'GAME','B', DATE '2026-01-01'), (3,'TV','C', DATE '2026-06-01');
INSERT INTO aod_ai.content_quality_score(content_id, quality_popularity_score) VALUES (1,0.9),(2,0.5),(3,0.7);
INSERT INTO aod_ai.content_fun_tag(content_id,tag,tag_score,tag_confidence) VALUES (1,'힐링',0.8,0.9),(2,'긴장감',0.7,0.6);
INSERT INTO aod_ai.content_embedding(content_id, embedding, model) VALUES
  (1, array_fill(0.1::real, ARRAY[1024])::vector, 'test'),
  (2, array_fill(0.2::real, ARRAY[1024])::vector, 'test'),
  (3, array_fill(0.3::real, ARRAY[1024])::vector, 'test');
