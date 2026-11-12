# 집결 | 개발·실행 안내

프로젝트 목적, 핵심 설계, 검증 근거와 운영 현황은 [저장소 소개 보고서](../../README.md)를 참고함. 이 문서는 실행과 개발에 필요한 절차를 정리함.

## 1. 실행 전제

- Java 21, Maven 실행 환경. 최초 의존성 다운로드에는 인터넷 연결 필요
- PostgreSQL 통합 테스트와 Docker 실행에는 Docker Desktop/Engine 필요
- 저장소 루트에서 아래 프로젝트 디렉터리로 이동

```sh
cd outputs/apartment-search
```

기본 실행은 빈 메모리 H2 DB임. 재시작하면 해당 환경의 데이터가 사라짐. 실제 수집 데이터를 보려면 해당 PostgreSQL DB에 연결해야 함. API 키 없이도 빈 검색 화면과 테스트를 실행할 수 있음.

## 2. 기본 개발 실행

```sh
# JDK가 없을 때만 실행: Python 3.12+ 필요
python3 scripts/setup-java.py
sh ./mvnw -v
sh ./mvnw spring-boot:run
```

기본 주소는 [localhost:8080](http://localhost:8080)임. IntelliJ에서는 이 디렉터리의 `pom.xml`을 열고 JDK 21로 `ApartmentApplication`을 실행함. `PORT=8081`을 지정하면 개발 포트를 변경할 수 있음. 포트 변경만으로 DB 연결이 변경되지 않음.

[PostgreSQL 개발 설정](docs/postgresql.md) · [환경 설정 상세](docs/development.md)

## 3. Docker 앱·DB 실행

```sh
# 최초 한 번만 복사. 이미 환경 파일이 있으면 덮어쓰지 않음.
cp .env.example .env.docker
# .env.docker의 DB_PASSWORD를 설정한 다음 실행
sh scripts/stack.sh up
sh scripts/stack.sh status
```

기본 접속 주소는 [localhost:8082](http://localhost:8082)임. 컨테이너 내부 앱 포트는 8080이며 외부 포트와 구분함. PostgreSQL 데이터는 Docker 볼륨에 저장됨. 처음 생성한 DB에는 실제 거래가 자동으로 적재되지 않음.

```sh
# 앱과 DB 중지 — 데이터 볼륨 유지
sh scripts/stack.sh stop
```

공공 API 수집은 별도 importer로 실행함. 수집 전 [공공 API 안내](docs/public-api.md)와 [전국 수집 안내](docs/nationwide-collection.md)를 참고해 대상 DB·지역·기간을 확인함. 운영 DB에 연습용 수집을 실행하지 않음.

[Docker 상세](docs/docker.md) · [배포 절차](docs/deployment.md)

## 4. 검증 명령

```sh
# 기본 Java 테스트
sh ./mvnw verify

# Docker가 필요하며 PostgreSQL 통합 테스트를 포함
sh ./mvnw -Ppostgres-it verify

# 외부 API를 호출하지 않는 브라우저 동작 모의 테스트
node scripts/test-bookmark-access.cjs
node scripts/test-detail-map.cjs

# 수집 재개 스크립트 테스트
python3 scripts/test_collect_nationwide.py
```

테스트 fixture는 실제 거래로 표시하지 않음. 공공 API를 부하 테스트 대상으로 사용하지 않음. CI 구성은 [워크플로](../../.github/workflows/ci.yml)를 참고함. k6 스크립트 준비와 성능 개선 검증 완료는 구분함.

## 5. 화면과 코드 위치

| 기능 | 주소 | 주요 코드 |
| --- | --- | --- |
| 검색 | `/`, `/api/trades` | `trade/`, `templates/index.html` |
| 상세·지도 | `/trades/{id}` | `templates/detail.html`, `static/js/detail-map.js` |
| 회원가입·로그인·내 계정 | `/signup`, `/login`, `/account` | `account/`, `common/security/` |
| 관심 목록 | `/saved`, `/api/account/bookmarks` | `bookmark/`, `templates/saved.html` |
| 수집 관리자 | `/admin` | `collection/jobs/`, `templates/admin.html` |

Java 패키지의 기준 경로는 `src/main/java/kr/aptfolio/`, 화면 리소스의 기준 경로는 `src/main/resources/`임. 관리 기능은 별도 활성화와 관리자 자격 증명 설정이 필요함.

## 6. 기능별 문서

- [사용자 로그인](docs/user-login.md) · [관리자 로그인](docs/admin-login.md)
- [계정별 관심 아파트](docs/bookmarks.md) · [지도 설정](docs/maps.md)
- [수집 작업 관리](docs/collection-jobs.md) · [검색 API](docs/api.md)
- [설계 결정](docs/decisions/001-mvp-design.md) · [과거 검증 기록](docs/verification.md)

날짜가 명시된 과거 문서는 당시 상태의 기록임. 현재 구현 범위는 저장소 소개 보고서와 소스 코드를 함께 확인해야 함. JavaScript 지도 키가 없으면 외부 검색 링크만 제공하며, 카카오 로그인은 아직 구현되지 않았음.

- [2026-11-10 로컬 수집 데이터 반영 기록](docs/local-data-transfer-20261110.md)

## 월별 거래 추이

거래 상세에서 같은 단지·전용면적의 6개월·1년·3년 평균 가격과 거래량을 조회할 수 있음. [집계 기준과 검증 결과](docs/monthly-trends.md)를 참고함.
