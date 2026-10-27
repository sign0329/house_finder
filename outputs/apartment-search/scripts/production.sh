#!/usr/bin/env sh
set -eu
umask 077
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
if ! command -v docker >/dev/null 2>&1 && [ -x /Applications/Docker.app/Contents/Resources/bin/docker ]; then
 PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"
 export PATH
fi
ENV_FILE=${DEPLOY_ENV_FILE:-$ROOT/.env.production}
COMPOSE_FILE=${DEPLOY_COMPOSE_FILE:-$ROOT/compose.production.yaml}
[ -f "$ENV_FILE" ] || { echo 'deploy/env.example을 .env.production으로 복사하고 도메인·DB 비밀번호를 설정하세요.' >&2; exit 1; }
compose() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }
case "${1:-}" in
 up) compose up -d --build --wait app proxy ;;
 status) compose ps ;;
 logs) compose logs --tail=100 app proxy ;;
 import) compose run --rm importer ;;
 backup)
  DEST=${BACKUP_DIR:-$ROOT/backups}
  mkdir -p "$DEST"
  chmod 700 "$DEST"
  TEMP=$(mktemp "$DEST/.backup-XXXXXXXX")
  trap 'rm -f "$TEMP"' EXIT HUP INT TERM
  compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$TEMP"
  [ -s "$TEMP" ] || { echo '빈 백업입니다.' >&2; exit 1; }
  FILE="$DEST/apartments-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
  mv "$TEMP" "$FILE"
  echo "백업 완료: $FILE"
  ;;
 stop) compose stop ;;
 *) echo '사용법: sh scripts/production.sh {up|status|logs|import|backup|stop}' >&2; exit 1 ;;
esac
