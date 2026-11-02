-- 이전 검증: RDS 와 대상에서 같은 파일을 돌리고 출력을 diff 한다(설계 §4).
--   psql -X -q -At -F ' ' -f verify.sql
-- 공용 스키마·aod_ai 테이블과 시스템 카탈로그만 읽는다 → aod_ai 계정으로도 돈다.
select 'rows.' || t, n from (
              select 'public.raw_items' as t, count(*) as n from public.raw_items
    union all select 'public.platform_data',     count(*) from public.platform_data
    union all select 'public.contents',          count(*) from public.contents
    union all select 'public.crawl_job_queue',   count(*) from public.crawl_job_queue
    union all select 'public.game_contents',     count(*) from public.game_contents
    union all select 'public.transform_runs',    count(*) from public.transform_runs
    union all select 'public.movie_contents',    count(*) from public.movie_contents
    union all select 'public.tv_contents',       count(*) from public.tv_contents
    union all select 'public.webtoon_contents',  count(*) from public.webtoon_contents
    union all select 'public.users',             count(*) from public.users
    union all select 'aod_ai.content_embedding', count(*) from aod_ai.content_embedding
) x
union all
select 'flyway.rows', count(*) from public.flyway_schema_history
union all
select 'flyway.max_version', max(version::int) from public.flyway_schema_history where version is not null
union all
select 'tables.' || n.nspname, count(*)
  from pg_class c join pg_namespace n on n.oid = c.relnamespace
 where c.relkind in ('r', 'p') and n.nspname in ('public', 'aod_ai', 'aod_log', 'aod_rec')
 group by n.nspname
union all
select 'ext.vector', count(*) from pg_extension where extname = 'vector'
order by 1;
