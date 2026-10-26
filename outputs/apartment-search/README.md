# 집결 · 아파트 실거래가 검색

Java 21 / Spring Boot 4.0.9 / Thymeleaf 웹 MVP와 개발 환경입니다.
검색, 정렬, 페이지 이동, 거래 상세, 동일 면적 거래 그래프, 브라우저 관심 저장을 제공합니다.
**모든 초기 거래는 가상 데이터입니다. 공공 API는 아직 연결하지 않았습니다.**

## 가장 빠른 실행 (보안 패치가 적용된 Java 21)

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

## 2026-10-26 보안 업데이트

- 현재 이 컴퓨터에는 프로젝트 내부 `.tools/java-home`에 Temurin 21.0.13을 설치했습니다.
- `./mvnw`는 해당 JDK를 우선 사용합니다. 시스템 `java` 명령/다른 프로젝트의 JDK는 바꾸지 않았습니다.
- 다른 컴퓨터에서는 Python 3.12+와 curl로 `python3 scripts/setup-java.py`를 실행하세요.
  공식 Adoptium 다운로드를 SHA-256 검증 후 프로젝트 내부에 설치합니다. Windows는 패치된 JDK 21/JAVA_HOME을 직접 설정하세요.
- IntelliJ Project SDK도 `.tools/java-home`로 지정하세요. 직접 `java -jar` 실행 시에는 이 경로의 `bin/java`를 사용하세요.
- 기본 demo는 127.0.0.1에만 바인딩합니다. postgres는 컨테이너 내부 바인딩을 유지하고 Compose가 호스트 노출을 제한합니다.
- 검색/상세 조회는 IP당 120회/분, 프로세스 전체 6,000회/분. 초과 시 429 + Retry-After를 반환합니다.
  다중 서버의 전역 제한이 아니며 프록시 뒤에서는 프록시 IP가 집계됩니다. 운영 프록시에서 별도 제한과 신뢰 경계를 설정하세요.
- SQL 쿼리 시간 제한 5초. 부하 테스트 시 제한을 유지한 실험과 제한값을 조정한 성능 실험을 구분하세요.
- CSP는 외부 스크립트와 프레이밍을 차단합니다. 차트의 동적 높이 표시 때문에 inline style만 허용합니다.
- 패키지 빌드 후 `python3 scripts/audit-dependencies.py`로 OSV를 조회할 수 있습니다.
  공개 Maven 좌표/버전만 전송하며 발견 또는 매핑 누락 시 비정상 종료합니다. `.m2/repository`가 필요합니다.
- Docker 이미지 최신화: `docker compose build --pull`. Docker/OS 이미지 취약점은 이 점검 범위에서 실제 스캔하지 않았습니다.
