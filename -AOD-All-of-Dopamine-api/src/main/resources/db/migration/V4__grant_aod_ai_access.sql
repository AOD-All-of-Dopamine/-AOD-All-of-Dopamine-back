-- V4: 백엔드 API 서빙 DB 계정(= spring.datasource.username, 현재 postgres)에
--     aod_ai 스키마 자산 읽기 + 로그 테이블 쓰기 권한 부여.
-- 근거: contracts §1.2, §2.2, §5. aod_ai 스키마/테이블은 M0 Python 마이그레이션 산출물(선행).
-- ※ 최소권한 전용 롤로 분리 시 이 대상 이름(postgres)만 교체하고 spring.datasource.username을 함께 재지정.
GRANT USAGE ON SCHEMA aod_ai TO postgres;

GRANT SELECT ON aod_ai.content_semantic_profile TO postgres;
GRANT SELECT ON aod_ai.content_fun_tag TO postgres;
GRANT SELECT ON aod_ai.content_embedding TO postgres;
GRANT SELECT ON aod_ai.content_quality_score TO postgres;
GRANT SELECT ON aod_ai.user_profile_cache TO postgres;
GRANT SELECT ON aod_ai.fun_tag_dict TO postgres;

GRANT INSERT ON aod_ai.rec_impression TO postgres;
GRANT INSERT ON aod_ai.rec_event TO postgres;

GRANT USAGE, SELECT ON SEQUENCE aod_ai.rec_impression_id_seq TO postgres;
GRANT USAGE, SELECT ON SEQUENCE aod_ai.rec_event_id_seq TO postgres;
