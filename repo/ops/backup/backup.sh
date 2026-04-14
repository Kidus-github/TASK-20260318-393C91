#!/bin/sh
set -eu

BACKUP_INTERVAL_SECONDS="${BACKUP_INTERVAL_SECONDS:-86400}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"
BACKUP_DIR="${BACKUP_DIR:-/backup}"

mkdir -p "$BACKUP_DIR"

while true; do
  timestamp="$(date +%Y%m%d_%H%M%S)"
  pg_dump -h "${POSTGRES_HOST:-postgres}" -U "${POSTGRES_USER:-citybus}" "${POSTGRES_DB:-citybus}" > "${BACKUP_DIR}/citybus_${timestamp}.sql"
  find "$BACKUP_DIR" -type f -name 'citybus_*.sql' -mtime +"$RETENTION_DAYS" -delete
  sleep "$BACKUP_INTERVAL_SECONDS"
done
