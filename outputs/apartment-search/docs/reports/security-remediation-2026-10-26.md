# 보안 보완 결과 — 2026-10-26

## 적용 사항

- Spring Boot 3.5.7 → 4.0.9, Spring Framework 7.0.10, Tomcat 11.0.27로 업데이트.
- 프로젝트 전용 Temurin Java 21.0.13 설치 및 SHA-256 검증. Maven 실행 스크립트가 이 JDK를 우선 사용.
- Boot 4의 MVC 테스트 및 Flyway 모듈 구성 반영.
- demo 프로필을 127.0.0.1에 바인딩.
- CSP, nosniff, 프레임 차단, Referrer-Policy, Permissions-Policy 적용. HTTPS 요청에는 HSTS 적용.
- 검색/상세 요청에 IP당 120회/분 및 프로세스 전체 6,000회/분 제한, 초과 시 429/Retry-After 반환.
- JDBC 쿼리 시간 제한 5초, HTTP 헤더 크기 제한 8KB, 전달 헤더 자동 신뢰 비활성화.
- 인라인 정렬 이벤트를 외부 JavaScript로 이동하여 CSP와 호환.
- Jenkins 이미지 빌드에 --pull 적용. 의존성 OSV 점검 스크립트 추가.

## 실행한 검증

- 수정 전 신규 보안 테스트 3개 실패 확인: 로컬 바인딩, 보안 헤더, 요청 제한.
- 수정 후 ./mvnw -o -q verify 통과: WebTest 8개, SecurityTest 3개, RequestLimitTest 2개, 총 13개.
- 패키징된 런타임 Maven 의존성 63개 OSV 조회: 매핑 누락 0개, 알려진 취약점 매칭 0개.
- 상세 조회 결과: dependency-scan-2026-10-26.json. 공개 Maven 좌표와 버전만 조회에 사용.
- JavaScript 및 셸 스크립트 구문 검사 통과.
- 실행 중이던 이 프로젝트의 IntelliJ Java 22 서버를 종료하고 패치된 JDK/JAR로 재시작.
- 실제 HTTP 응답 200, 보안 헤더, /actuator/health의 UP, 127.0.0.1:8080 리스닝 확인.

## 범위 및 남은 사항

- OSV 결과는 조회 시점에 등록된 취약점 기준이며 모든 취약점 부재를 보장하지 않는다.
- 시스템 Java 및 IntelliJ 기본 SDK는 변경하지 않았다. IntelliJ에서 Maven 프로젝트를 다시 불러오고 Project SDK와 실행 구성 JRE를 프로젝트 .tools/java-home로 설정해야 한다.
- 소스 ZIP에는 JDK 및 Maven 캐시를 포함하지 않는다. 새 환경에서는 README의 scripts/setup-java.py 또는 패치된 JDK 21을 사용한다.
- 요청 제한은 프로세스 내 메모리 방식이다. 여러 서버를 운영할 때는 공유 저장소나 게이트웨이 제한이 필요하다. 프록시 환경에서는 신뢰할 프록시 설정과 IP 집계 정책을 설계해야 한다.
- 차트의 동적 스타일 때문에 CSP style-src에 unsafe-inline을 허용한다. script-src에는 허용하지 않는다.
- Docker/OS 이미지 스캔, PostgreSQL 통합 검증, k6 부하 테스트는 이 작업에서 실행하지 않았다.
- H2 2.4.240은 Flyway가 검증한 H2 버전보다 새롭다는 경고가 있다. 실제 마이그레이션과 H2 테스트는 통과했다.
