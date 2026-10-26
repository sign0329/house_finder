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

종로구 API 수집은 별도 시작 옵션으로 지원한다. 회원 로그인/운영 배포는 미구현이다.
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

요청은 WebSecurityFilter를 통과한다. SQL 값은 바인딩하며 정렬 SQL은 허용 목록에서 선택한다. 기본 demo는 로컬 접속만 허용한다. 전달 헤더를 자동 신뢰하지 않고 요청 제한은 실제 연결 IP에 적용한다. 따라서 프록시 뒤에서는 프록시 IP로 집계될 수 있으며 운영 구성에서 별도 설계가 필요하다.

## 공공데이터 수집 경로

MolitImportRunner → MolitClient(HTTPS/페이지 검증) → MolitXml → MolitStore(트랜잭션). molit 프로필은 별도 파일 H2를 사용한다. 조회는 기존 SearchService를 재사용한다. V2는 수집 식별자·월·스냅샷·성공 시각, V3는 면적 소수 정밀도를 추가한다. [수집 정책과 한계](public-api.md)를 참고한다.
