# 개발 및 실행 안내

## Docker + PostgreSQL (설정 준비, 실행 미검증)

Docker Engine/Desktop과 Compose가 필요하다. 프로젝트 루트에서:

```sh
cp .env.example .env
# .env의 DB_PASSWORD를 변경한 뒤 실행
# 다른 서버가 8080을 사용 중이면 종료하거나 .env의 PORT 변경
docker compose build --pull
docker compose up -d
docker compose logs -f app
```

종료는 `docker compose down`. DB 볼륨은 유지된다. 앱 포트는 호스트 127.0.0.1에 노출되고 DB·Redis 포트는 호스트에 공개하지 않는다. Redis는 `--profile cache`로 켤 수 있으나 앱 캐싱은 미구현이다.

## 환경 변수

| 변수 | 기본 / 용도 |
| --- | --- |
| PORT | 8080; 직접 실행은 서버 포트, Compose는 호스트 포트 |
| SPRING_PROFILES_ACTIVE | 미지정 시 demo, Compose는 postgres |
| DB_URL | postgres 직접 실행: jdbc:postgresql://localhost:5432/apartments |
| DB_USER | apartments |
| DB_PASSWORD | postgres에서 필수, 실제 값은 커밋 금지 |
| DB_POOL_SIZE | 10 |
| DEMO_DATA | postgres 기본 false, .env.example은 체험용 true |
| REQUESTS_PER_MINUTE | 직접 실행 시 IP당 기본 120 |
| GLOBAL_REQUESTS_PER_MINUTE | 직접 실행 시 프로세스 전체 기본 6000 |

`.env`는 Compose가 읽는다. `./mvnw`는 자동으로 읽지 않으므로 직접 실행 시 셸 환경 변수를 설정한다. Compose의 요청 제한 변경은 app.environment에 해당 변수를 명시적으로 전달해야 한다.

샘플은 테이블이 비었을 때만 들어간다. DEMO_DATA를 false로 바꿔도 기존 샘플은 삭제되지 않는다. 실제 수집 데이터는 별도 DB로 시작한다.

## 부하 테스트 (미실행)

```sh
k6 run load-tests/search.js
```

현재 스크립트는 기본 5 VU, 30초, 기준 p95 < 1초 / 요청 실패율 < 1%다. 기준값은 측정 결과가 아니다. 기본 IP당 120회/분 제한으로 429가 발생해 실패할 수 있다. 제한 동작 검증과 조회 성능 실험은 구분한다.

조회 성능만 측정할 때는 격리된 로컬 앱을 아래와 같이 별도 포트에 실행할 수 있다. 운영 설정으로 사용하지 않는다.

```sh
PORT=8081 REQUESTS_PER_MINUTE=100000 GLOBAL_REQUESTS_PER_MINUTE=100000 ./mvnw spring-boot:run
# 별도 터미널에서 실행
BASE_URL=http://localhost:8081 k6 run load-tests/search.js
```

공공 API를 BASE_URL로 지정하지 않는다. 결과를 기록할 때 하드웨어, JDK/DB, 데이터 건수, 인덱스, 워밍업, VU, 기간, 요청 제한값, p95, 요청 수와 오류율을 함께 남긴다.

## Jenkins

Jenkinsfile은 Maven clean verify, Docker 이미지 빌드, JUnit 결과 및 JAR 보관 단계를 정의한다. 에이전트에는 패치된 JDK 21, curl, tar, Docker 권한이 필요하다. 실제 파이프라인 실행 및 자동 배포는 아직 검증하지 않았다.

## 공공데이터 프로필

`molit` 프로필은 파일 H2와 로컬 바인딩을 사용한다. IntelliJ의 인증키와 Program arguments 설정은 [첫 수집 안내](public-api.md)를 따른다. 수집은 기본 비활성화이며 시작 옵션으로만 실행한다.
