#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if ! command -v docker >/dev/null 2>&1 && [ -x /Applications/Docker.app/Contents/Resources/bin/docker ]; then
 PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"
 export PATH
fi
command -v docker >/dev/null 2>&1 || { echo 'Docker Desktop/CLI를 설치하고 실행하세요.' >&2; exit 1; }
cd "$ROOT"
[ -f .env.docker ] || { echo '.env.example을 .env.docker로 복사하고 DB_PASSWORD를 설정하세요.' >&2; exit 1; }
compose() { docker compose --env-file .env.docker -f compose.yaml "$@"; }
case "${1:-}" in
 up) compose up -d --build --wait app ;;
 import) compose run --rm importer ;;
 status) compose ps ;;
 logs) compose logs --tail=100 app db ;;
 restart) compose restart app; compose up -d --wait app ;;
 stop) compose stop ;;
 down) compose down ;;
 *) echo '사용법: sh scripts/stack.sh {up|import|status|logs|restart|stop|down}' >&2; exit 1 ;;
esac
