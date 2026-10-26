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
