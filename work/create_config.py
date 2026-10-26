exec(open('work/create_web.py').read().split("put('pom.xml'")[0])
put('mvnw', '''
#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
VERSION=3.9.9
DEST="$ROOT/.tools/apache-maven-$VERSION"
if [ ! -x "$DEST/bin/mvn" ]; then
 mkdir -p "$ROOT/.tools"
 ARCHIVE="$ROOT/.tools/maven.tar.gz"
 URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$VERSION/apache-maven-$VERSION-bin.tar.gz"
 curl --fail --location --retry 2 "$URL" -o "$ARCHIVE"
 EXPECTED=$(curl --fail --location "$URL.sha512")
 if command -v sha512sum >/dev/null 2>&1; then ACTUAL=$(sha512sum "$ARCHIVE" | cut -d ' ' -f 1); else ACTUAL=$(shasum -a 512 "$ARCHIVE" | cut -d ' ' -f 1); fi
 [ "$EXPECTED" = "$ACTUAL" ] || { echo "Maven checksum mismatch" >&2; exit 1; }
 tar -xzf "$ARCHIVE" -C "$ROOT/.tools"
 rm "$ARCHIVE"
fi
exec "$DEST/bin/mvn" -Dmaven.repo.local="$ROOT/.m2/repository" "$@"
''')
put('.gitignore','''
target/
.tools/
.m2/
.env
.idea/
*.iml
.DS_Store
load-results/
''')
put('.dockerignore','''
.git
.tools
.m2
target
.env
.idea
load-results
''')
put('.env.example','''
DB_USER=apartments
DB_PASSWORD=change-this-local-password
DB_POOL_SIZE=10
DEMO_DATA=true
PORT=8080
# Reserved for the next phase. No collector calls this API yet.
# MOLIT_SERVICE_KEY=
''')
put('Dockerfile','''
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build --chown=app:app /workspace/target/apartment-search-0.1.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
''')
put('compose.yaml','''
services:
  app:
    build: .
    ports:
      - "127.0.0.1:${PORT:-8080}:8080"
    environment:
      SPRING_PROFILES_ACTIVE: postgres
      DB_URL: jdbc:postgresql://db:5432/apartments
      DB_USER: ${DB_USER:-apartments}
      DB_PASSWORD: ${DB_PASSWORD:?Copy .env.example to .env and set DB_PASSWORD}
      DB_POOL_SIZE: ${DB_POOL_SIZE:-10}
      DEMO_DATA: ${DEMO_DATA:-false}
    depends_on:
      db:
        condition: service_healthy
    restart: unless-stopped
  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: apartments
      POSTGRES_USER: ${DB_USER:-apartments}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?Set DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d apartments"]
      interval: 5s
      timeout: 3s
      retries: 15
    restart: unless-stopped
  redis:
    image: redis:7.4-alpine
    profiles: [cache]
    command: ["redis-server", "--maxmemory", "128mb", "--maxmemory-policy", "allkeys-lru"]
    # Optional infrastructure only; application caching is a future task.
volumes:
  pgdata:
''')
put('Jenkinsfile','''
pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds() }
  stages {
    stage('Build and test') { steps { sh './mvnw -B clean verify' } }
    stage('Docker image') { steps { sh 'docker build -t apartment-search:${BUILD_NUMBER} .' } }
  }
  post {
    always { junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml' }
    success { archiveArtifacts artifacts: 'target/*.jar', fingerprint: true }
  }
}
''')
put('AGENTS.md','''
# Apartment Search working agreement

- Stack: Java 21, Spring Boot, Thymeleaf, JDBC, PostgreSQL, Flyway.
- Read docs/requirements.md and docs/architecture.md before changing behavior.
- Keep sample data clearly labeled; never present generated trades as public data.
- Add a meaningful failing test before changing a domain rule, observe the failure, implement, then run ./mvnw verify.
- Do not claim TDD evidence unless the red and green runs were actually observed.
- Store prices as integer 만원 and areas as BigDecimal. Validate all search inputs.
- SQL values use named parameters; sorting uses an explicit allowlist.
- Change schema only through a new Flyway migration.
- Never load test a public API. Use fixtures or a mock source.
- Never commit credentials or .env; do not weaken tests to make CI green.
- Report tests actually executed and environment limitations.
- No deployment, paid resources or API collection are required by the current scope.
''')
put('docs/requirements.md','''
# 웹 MVP 범위

## 완료 조건
- 지역, 아파트/구 이름, 계약 기간, 만원 단위 가격, 면적 검색.
- 날짜/가격 경계는 포함. 면적: 60 미만, 60~85 포함, 85 초과.
- 최근순/가격순 정렬 및 12건 페이지네이션. 검색 조건 유지.
- 같은 주소·단지·면적의 최근 최대 12건을 상세에서 표시.
- 관심 단지는 localStorage에 저장. 계정 동기화는 제공하지 않음.
- 데모는 고정 시드의 가상 거래 240건. 실제 공공데이터와 혼합하지 않음.
- 잘못된 입력은 400, 없는 거래는 404, 빈 결과는 안내 문구 표시.

## 다음 단계
- 국토교통부 수집 API 명세/샘플 검증, 지역·월 스냅샷과 취소/정정 설계.
- Spring Batch 수집 및 재시작, PostgreSQL Testcontainers 통합 테스트.
- 대량 적재, EXPLAIN ANALYZE, 키셋 페이지네이션, Redis 캐시 비교.
- 회원 인증, 실제 배포 및 부하 실험.
''')
put('docs/architecture.md','''
# 구조와 선택

브라우저 → Spring MVC/Thymeleaf → SearchService → JDBC → H2(demo) / PostgreSQL(postgres).
Flyway로 두 환경에 동일한 기본 테이블과 인덱스를 생성한다.

기본 demo 프로필은 외부 서비스 없이 부팅되도록 메모리 H2를 사용한다. 재시작 시 초기화된다.
postgres 프로필은 DB_PASSWORD를 요구하고 기본적으로 샘플 데이터 입력을 하지 않는다.
compose 데모를 사용하려면 .env.example의 DEMO_DATA=true를 사용한다.
샘플 데이터는 DB가 비었을 때만 입력한다. 플래그를 false로 바꿔도 기존 데이터는 삭제하지 않는다.
실제 데이터 연결 시 별도 DB로 시작한다.

거래 상세 URL은 거래 ID를 가리킨다. 관심 저장은 주소와 이름으로 묶지만 링크는 저장 시점의 거래 ID다.
수집 설계 단계에서 안정적인 단지 식별자로 바꿔야 한다.

현재 OFFSET + COUNT 방식은 명확한 초기 기준선이다. 대용량 처리 완료를 의미하지 않는다.
region/deal_date 및 apartment/deal_date 인덱스를 둔다. 부분 문자열 검색은 인덱스 효과가 제한된다.
Redis는 선택적 인프라만 제공하며 실제 캐싱은 아직 연결하지 않았다.

API 수집/회원 로그인/운영 배포는 이번 웹·환경 설정 범위 밖이다.
AWS 배포 후보: EC2 + Docker, 필요에 따라 RDS 분리. 리소스는 생성하지 않았다.
''')
put('load-tests/search.js','''
import http from 'k6/http';
import { check, sleep } from 'k6';
export const options = {
  vus: Number(__ENV.VUS || 5), duration: __ENV.DURATION || '30s',
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000'] }
};
export default function () {
  const base = __ENV.BASE_URL || 'http://localhost:8080';
  const region = ['서울', '경기', '부산', '대전'][__ITER % 4];
  const r = http.get(`${base}/api/trades?region=${encodeURIComponent(region)}&sort=recent`);
  check(r, {'HTTP 200': x => x.status === 200, 'valid result': x => {try {return Array.isArray(x.json('items'));}catch {return false;}}});
  sleep(0.2);
}
''')
put('README.md','''
# 집결 · 아파트 실거래가 검색

Java 21 / Spring Boot 3.5.7 / Thymeleaf 웹 MVP와 개발 환경입니다.
검색, 정렬, 페이지 이동, 거래 상세, 동일 면적 거래 그래프, 브라우저 관심 저장을 제공합니다.
**모든 초기 거래는 가상 데이터입니다. 공공 API는 아직 연결하지 않았습니다.**

## 가장 빠른 실행 (Java 21 이상)

```sh
./mvnw spring-boot:run
```

http://localhost:8080 접속. Maven이 없으면 프로젝트 내부 .tools에 내려받아 SHA-512를 검증합니다.
최초 실행에 인터넷이 필요합니다. 시스템 Maven이 있으면 해당 Maven을 사용합니다.
기본 demo 프로필은 H2 메모리 DB를 사용하므로 Docker나 API 키가 필요 없습니다.

## Docker + PostgreSQL

Docker Desktop(또는 Docker Engine/Compose)을 설치한 환경에서:

```sh
cp .env.example .env
# .env의 DB_PASSWORD 수정
 docker compose up --build -d
 docker compose logs -f app
```

http://localhost:8080 접속. 앱 포트는 로컬 주소에만 노출됩니다. DB/Redis는 외부에 노출하지 않습니다.
선택적으로 `docker compose --profile cache up -d`로 Redis를 켤 수 있지만 앱 캐시는 아직 미연결입니다.
종료: `docker compose down`. PostgreSQL 데이터 볼륨은 유지됩니다.

## 검증

```sh
./mvnw verify
k6 run load-tests/search.js
```

현재 테스트는 H2 기반 웹 통합 테스트입니다. PostgreSQL 검증 및 Testcontainers는 후속 작업입니다.
k6 기본값은 5 VU / 30초의 스모크 부하이고, 대용량 성능 검증 결과가 아닙니다.
외부 공공 API로 BASE_URL을 지정하지 마세요.
Jenkins 에이전트에는 Java 21+, curl, tar, Docker 접근 권한이 필요합니다.
Jenkinsfile은 테스트/이미지 빌드까지 수행하며 자동 클라우드 배포는 하지 않습니다.

## 주요 경로

- `/` 검색 화면
- `/trades/{id}` 거래 상세
- `/saved` 현재 브라우저의 관심 단지
- `/api/trades` 동일 검색 조건의 JSON 결과
- `/actuator/health` 상태 확인

검색 파라미터: q, region, minPrice, maxPrice(만원), from, to(YYYY-MM-DD),
area(small/medium/large), sort(recent/priceAsc/priceDesc), page(0부터).

`docs/requirements.md`, `docs/architecture.md`, `docs/verification.md`, `AGENTS.md`를 참고하세요.
''')
put('docs/verification.md','''
# 검증 기록

테스트 코드를 운영 코드보다 먼저 작성했으나, 초기 Maven 부재로 Red 실행은 관측하지 못했다.
따라서 최초 구현을 완전한 Red–Green TDD 수행으로 주장하지 않는다.
실제 빌드/실행 결과는 검증 후 이 파일에 기록한다.
''')
(p/'mvnw').chmod(0o755)
