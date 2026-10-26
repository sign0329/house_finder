# PostgreSQL 개발 환경과 통합 테스트

기존 H2(molit/demo)는 그대로 유지하며 PostgreSQL을 별도 환경으로 실행한다. H2 파일을 자동 변환하거나 삭제하지 않는다.

## 1. 개발 DB 실행

Docker Desktop을 실행한 뒤 `pom.xml`이 있는 프로젝트 폴더에서:

```sh
cp .env.example .env.postgres
# .env.postgres의 DB_PASSWORD를 직접 변경한다.
# 실제 공공데이터를 사용할 때 DEMO_DATA=false로 설정한다.
docker compose --env-file .env.postgres -f compose.postgres.yaml up -d --wait
```

DB는 `127.0.0.1:5433`에만 노출된다. `house-finder-postgres` 프로젝트의 별도 볼륨을 사용하며 기본 compose.yaml과 독립적이다. 포트를 바꾸려면 `.env.postgres`에 `POSTGRES_PORT`를 지정한다. `docker`가 PATH에 없는 macOS는 Docker Desktop 설정에서 CLI를 설치하거나 `/Applications/Docker.app/Contents/Resources/bin/docker`를 사용한다.

## 2. IntelliJ 앱 연결

기존 실행 구성을 복제하여 `ApartmentApplication-Postgres`로 이름을 지정한다. Working directory는 프로젝트 폴더이며 Environment variables는 아래 값으로 설정한다.

```text
DB_URL=jdbc:postgresql://127.0.0.1:5433/apartments
DB_USER=apartments
DB_PASSWORD=<.env.postgres에 설정한 비밀번호>
DEMO_DATA=false
```

기존 `MOLIT_SERVICE_KEY`를 유지한다. Program arguments:

```text
--spring.profiles.active=postgres --server.address=127.0.0.1 --server.port=8081 --molit.import-on-start=true --molit.month=202609
```

수집 완료 후 http://localhost:8081 에서 확인한다. 기존 H2 앱은 8080에서 계속 사용할 수 있다. 두 DB는 별도 데이터이며 동기화하지 않는다. 첫 수집 후 조회만 할 때는 `--molit.import-on-start=true`를 제거한다. 앱은 `.env.postgres`를 자동으로 읽지 않으므로 IntelliJ 환경 변수에 직접 지정한다.

관심 저장은 브라우저 origin(포트 포함)별로 구분된다. H2의 기존 관심 목록이 다른 포트의 PostgreSQL에 자동 이전되지 않는다.

## 3. 실제 PostgreSQL 통합 테스트

```sh
# 기존 테스트 30개만: Docker 없이 가능
./mvnw verify

# 기존 테스트 + PostgreSQL 통합 테스트 (Docker 필요)
sh scripts/test-postgres.sh
```

스크립트는 Docker CLI와 현재 컨텍스트를 확인하고, macOS Docker Desktop의 컨테이너 소켓 경로를 맞춘 뒤 `./mvnw -Ppostgres-it verify`를 실행한다. CI에서는 이 Maven 명령을 직접 실행한다.

Testcontainers가 PostgreSQL 17 테스트 컨테이너를 임의 포트로 생성한다. 개발 DB의 주소·비밀번호를 사용하지 않으며 종료 후 컨테이너를 정리한다. Docker가 없으면 통합 테스트를 성공으로 건너뛰지 않고 실패한다. 최초 실행에는 Maven 의존성 및 Docker 이미지 다운로드가 필요하다.

- Flyway V1~V3 실제 적용과 면적 소수 4자리
- 합성 XML → 저장 → 검색 API·상세 조회
- 동일 거래 중복 횟수·ID 유지 및 LIKE 와일드카드 처리
- 정정·취소·빈 월 갱신 및 다른 월 데이터 보존
- PostgreSQL 제약조건 오류 시 전체 트랜잭션 롤백
- 동시 수집 시 하나의 완전한 스냅샷으로 직렬화
- 가상 데이터가 있는 DB에 수집 거부

결과: `target/surefire-reports`(기존), `target/failsafe-reports`(PostgreSQL). GitHub의 필수 `Java tests` 검사는 `-Ppostgres-it`를 사용한다. 실제 국토부 API는 테스트 중 호출하지 않는다.

## 4. 종료와 데이터 유지

```sh
docker compose --env-file .env.postgres -f compose.postgres.yaml down
```

볼륨은 유지된다. `down -v`는 DB를 삭제하므로 초기화 의도가 없다면 사용하지 않는다. `.env.postgres`의 비밀번호를 바꿔도 기존 PostgreSQL 볼륨의 계정 비밀번호가 자동으로 바뀌지는 않는다.

## 검증 범위

자동 테스트는 합성 거래로 데이터베이스 동작을 검증한다. 성능·대용량 처리·운영 백업 및 복구는 별도 검증이 필요하다. 실제 호출과 개발 DB 실행 결과는 [검증 기록](verification.md)에 구분해서 기록한다.
