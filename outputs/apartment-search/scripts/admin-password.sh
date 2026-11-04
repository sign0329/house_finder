#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
jar=target/apartment-search-0.1.0.jar
if [ ! -f "$jar" ]; then
  echo '먼저 sh ./mvnw package 로 앱을 빌드하세요.' >&2
  exit 1
fi
java_cmd=java
if [ -n "${JAVA_HOME:-}" ]; then java_cmd="$JAVA_HOME/bin/java"; fi
exec "$java_cmd" -Dloader.main=kr.aptfolio.common.security.AdminPasswordHash -cp "$jar" org.springframework.boot.loader.launch.PropertiesLauncher
