#!/bin/sh
# 빈 데이터 디렉터리로 처음 뜰 때만 docker-entrypoint 가 실행한다(설계 §4).
# RDS 덤프의 GRANT·기본 권한이 가리키는 롤을 복원 전에 만들어 둔다.
# 롤 멤버십(aod_ai ∈ aod_public_ro)은 pg_dump 에 들어가지 않아서 여기서 만든다.
set -e
if [ -z "${AOD_AI_PASSWORD:-}" ]; then
  echo "AOD_AI_PASSWORD 가 필요하다" >&2
  exit 1
fi
psql -v ON_ERROR_STOP=1 -v ai_pw="$AOD_AI_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE aod_public_ro NOLOGIN;
CREATE ROLE aod_ai LOGIN PASSWORD :'ai_pw';
GRANT aod_public_ro TO aod_ai;
SQL
