#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if ! command -v docker >/dev/null 2>&1 && [ -x /Applications/Docker.app/Contents/Resources/bin/docker ]; then
 PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"
 export PATH
fi
command -v docker >/dev/null 2>&1 || { echo 'Docker CLI가 필요합니다. Docker Desktop을 실행하세요.' >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo 'Docker 엔진에 연결할 수 없습니다. Docker Desktop을 실행하세요.' >&2; exit 1; }
if [ -z "${DOCKER_HOST:-}" ]; then
 DOCKER_HOST=$(docker context inspect --format '{{.Endpoints.docker.Host}}')
 export DOCKER_HOST
fi
if [ "$(uname -s)" = Darwin ] && [ "$(docker info --format '{{.OperatingSystem}}')" = 'Docker Desktop' ]; then
 TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}
 export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE
fi
cd "$ROOT"
exec sh ./mvnw -Ppostgres-it verify "$@"
