# 구조와 선택

브라우저 → Spring MVC/Thymeleaf → SearchService → JDBC → H2(local) / PostgreSQL(postgres).
Flyway로 두 환경에 동일한 기본 테이블과 인덱스를 생성한다.

기본 local 프로필은 외부 서비스 없이 부팅되도록 메모리 H2를 사용한다. 재시작 시 초기화된다.
postgres 프로필은 DB_PASSWORD를 요구하고 기본적으로 샘플 데이터 입력을 하지 않는다.
자동 샘플 데이터 생성과 DEMO_DATA 설정은 제거했다. 테스트 fixture는 테스트 소스에만 존재한다.
실제 데이터 연결 시 별도 DB로 시작한다.

거래 상세 URL은 거래 ID를 가리킨다. 관심 저장은 주소와 이름으로 묶지만 링크는 저장 시점의 거래 ID다.
수집 설계 단계에서 안정적인 단지 식별자로 바꿔야 한다.

현재 OFFSET + COUNT 방식은 명확한 초기 기준선이다. 대용량 처리 완료를 의미하지 않는다.
region/deal_date 및 apartment/deal_date 인덱스를 둔다. 부분 문자열 검색은 인덱스 효과가 제한된다.
Redis는 선택적 인프라만 제공하며 실제 캐싱은 아직 연결하지 않았다.

지역·기간 지정 API 수집은 별도 시작 옵션으로 지원한다. 회원 로그인/운영 배포는 미구현이다.
AWS 배포 후보: EC2 + Docker, 필요에 따라 RDS 분리. 리소스는 생성하지 않았다.

## 거래 모델

`trade`는 거래 단위 테이블이다. `id`는 내부 식별자이며 공공데이터의 고유 ID가 아니다.

| 필드 | 저장 방식 |
| --- | --- |
| region, district, apartment, address | 지역·단지·주소 문자열 |
| area | NUMERIC(12,4), Java BigDecimal, ㎡ |
| floor, built_year | 정수 |
| deal_date | DATE |
| price | BIGINT, 만원 |
| source | 데이터 출처, 초기값 DEMO |

스키마 원본은 [V1 마이그레이션](../src/main/resources/db/migration/V1__create_trade.sql)이다.

## 보안 경계

요청은 WebSecurityFilter를 통과한다. SQL 값은 바인딩하며 정렬 SQL은 허용 목록에서 선택한다. 기본 local은 로컬 접속만 허용한다. 전달 헤더를 자동 신뢰하지 않고 요청 제한은 실제 연결 IP에 적용한다. 따라서 프록시 뒤에서는 프록시 IP로 집계될 수 있으며 운영 구성에서 별도 설계가 필요하다.

## 공공데이터 수집 경로

MolitImportRunner → MolitPlan(범위 검증) → MolitCollector(구간 순차 실행) → MolitClient(HTTPS/페이지 검증) → MolitXml → MolitStore(트랜잭션). molit 프로필은 별도 파일 H2를 사용한다. 조회는 기존 SearchService를 재사용한다. V2는 수집 식별자·월·스냅샷·성공 시각, V3는 면적 소수 정밀도를 추가한다. [수집 정책과 한계](public-api.md)를 참고한다.

## PostgreSQL 검증 환경

`compose.postgres.yaml`은 IntelliJ 앱을 위한 전용 PostgreSQL 개발 DB다. Testcontainers 통합 테스트는 개발 DB를 사용하지 않고 새 PostgreSQL 17 컨테이너를 임의 포트로 만들고 정리한다. Maven `postgres-it` 프로필에서 Failsafe를 실행하며 Docker가 없으면 실패한다. CI의 필수 Java tests에 이 프로필을 포함한다.

V4는 source_region을 추가하고 기존 종로구 행을 보정한다. 스냅샷 삭제는 지역 코드와 계약월로 제한한다. 구간별 트랜잭션이라 일부 실패해도 성공한 구간은 유지된다. 상세 실행 정책은 public-api.md를 따른다.

## 기능별 패키지 구조

```text
kr.aptfolio
├── ApartmentApplication       # IDE/배포의 실행 진입점 유지
├── trade                      # 거래 검색·상세·화면·시도 선택
│   ├── Trade
│   ├── SearchService
│   ├── WebController
│   └── Provinces
├── collection.molit           # 국토부 API·수집 계획·저장
└── common.security            # 웹 보안 및 요청 제한
```

기능 내부 클래스는 같은 패키지에 모은다. 현재 크기에서는 controller/service/repository 하위 패키지를 추가하지 않는다. 테스트도 trade, collection.molit, common.security에 대응시키며, 여러 기능을 함께 검증하는 PostgreSQL 통합 테스트는 postgres에 유지한다.

패키지 이동만 수행했으며 클래스 이름, URL, 응답, SQL, 설정 키, 마이그레이션은 유지한다. ApartmentApplication은 루트에 있어 하위 패키지가 기존 컴포넌트 스캔에 포함된다. IDE 실행 클래스도 바꿀 필요가 없다.

업데이트 후에는 Maven clean verify 또는 IDE Rebuild로 이전 패키지의 컴파일 산출물을 제거한다. 오래된 target/classes를 남겨 두면 이전·새 패키지의 동일 이름 빈이 함께 발견될 수 있다. Docker 이미지는 새 소스로 다시 빌드해야 한다. 실행 중인 컨테이너나 수집 데이터는 이 코드 변경만으로 갱신되지 않는다.

기존 Flyway V1의 source 기본값 DEMO는 체크섬 호환을 위해 변경하지 않았다. 운영 저장 경로는 출처 MOLIT를 명시하며, 레거시 비-MOLIT 데이터 혼합 거부 로직도 유지한다. 과거 검증 문서의 240건 데모 기록은 당시 결과다.

## 수집 작업 이력

collection.jobs는 V6 collection_job에 지역·월별 시도 이력을 저장한다. 기존 importer와 선택적으로 활성화하는 관리 API가 같은 작업 서비스에 연결된다. 거래 스냅샷과 성공 기록은 동일 트랜잭션으로 처리하며, 실패 기록은 별도 트랜잭션이다. 기존 전역 수집 잠금으로 중복 claim을 직렬화하고 30분 유효 시간과 완료 전 상태 확인으로 만료 작업의 늦은 저장을 차단한다. 상세 API·복구 규칙은 [수집 이력 안내](collection-jobs.md)를 따른다.

## 관리자 인증

common.security의 AdminSecurityConfiguration이 관리자 화면/세션 API, 선택적인 자동화 토큰 API, 공개 페이지의 체인을 분리한다. 관리자 계정은 BCrypt 해시 환경 설정으로 구성하고 Spring Security 세션·CSRF·로그아웃을 사용한다. 활성화/운영 제약은 [관리자 로그인](admin-login.md)을 따른다.

## 사용자 계정

account 패키지의 UserAccounts/AccountController가 회원가입·로그인용 계정 조회와 내 정보 화면을 담당한다. V7 user_account에 계정을 저장하고 사용자와 관리자 로그인에 각각 별도의 AuthenticationManager를 지정한다. 상세 범위와 운영 제약은 [사용자 로그인](user-login.md)을 따른다.
