#!/usr/bin/env bash
# AOD Postgres 일일 백업 — systemd aod-backup.timer 가 03:30 KST 에 부른다(설계 §10).
#   pg_dump(커스텀 포맷) → $AOD_BACKUP_DIR, $AOD_BACKUP_KEEP_DAYS 일 보관.
#   rclone 리모트 r2: 가 설정돼 있으면 r2:aod-backups/ 에도 복사한다(오프사이트).
set -euo pipefail

BACKUP_DIR="${AOD_BACKUP_DIR:-/srv/aod-backups}"
KEEP_DAYS="${AOD_BACKUP_KEEP_DAYS:-14}"
CONTAINER="${AOD_PG_CONTAINER:-aod-postgres}"
STAMP="$(TZ=Asia/Seoul date +%Y%m%d-%H%M)"
FILE="$BACKUP_DIR/aod-$STAMP.dump"

mkdir -p "$BACKUP_DIR"
trap 'rm -f "$FILE.partial"' EXIT

docker exec "$CONTAINER" pg_dump -U postgres -d postgres -Fc -Z6 > "$FILE.partial"
# 목차가 읽히는 덤프만 보관 목록에 올린다
docker exec -i "$CONTAINER" pg_restore -l < "$FILE.partial" > /dev/null
mv "$FILE.partial" "$FILE"

find "$BACKUP_DIR" -maxdepth 1 -name 'aod-*.dump' -mtime +"$KEEP_DAYS" -delete

if command -v rclone > /dev/null 2>&1 && rclone listremotes 2> /dev/null | grep -qx 'r2:'; then
  rclone copy "$FILE" r2:aod-backups/
  echo "offsite: r2:aod-backups/$(basename "$FILE")"
fi

echo "OK $FILE $(du -h "$FILE" | cut -f1)"
