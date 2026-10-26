# 보안 점검 — 2026-10-26

## 범위와 제한
pom.xml, 기존 빌드 JAR의 BOOT-INF/lib, Java 소스, 템플릿/JS, 설정/Docker/Jenkins 파일을 수동 확인하고 공식 보안 공지와 대조했다.
전체 의존성 자동 SCA, 컨테이너 이미지 스캔, 침투 테스트는 실행하지 않았다. 아래 목록은 전체 CVE 목록이 아니다.
서버 실행과 취약점 악용 테스트, 코드 변경은 하지 않았다.

## 확인한 버전
로컬 java -version: OpenJDK 22.0.2 (2024-07-16).
빌드 타깃: Java 21. Docker는 eclipse-temurin:21-jre지만 실제 이미지 패치 버전은 확인하지 못했다.
빌드 JAR: Spring Boot 3.5.7, Framework 6.2.12, Tomcat 10.1.48,
Logback 1.5.20, Jackson 2.19.2, H2 2.3.232, PostgreSQL JDBC 42.7.8.

## 우선 조치
1. 로컬 JDK 업데이트. 21 타깃 설정은 실제 런타임 22.0.2를 업데이트하지 않는다.
   Oracle의 22.0.2 안내는 2024-10-15 이후 사용을 권장하지 않는다.
   로컬 OpenJDK 공급자별 정책과 별개로 오래된 런타임을 최신 보안 패치가 적용된 JDK 21 LTS로 교체하는 것이 적절하다.
   https://www.oracle.com/java/technologies/javase/22all-relnotes.html
2. Spring Boot 의존성 세트를 업데이트하고 실제 해결된 버전을 재검사해야 한다.
   특정 라이브러리 하나의 최소 수정 버전만 적용한 뒤 전체가 안전하다고 판정하면 안 된다.

## 공식 공지와 버전 대조 (실제 악용 가능성은 별도)
- CVE-2026-41848 / Spring AntPathMatcher ReDoS / 공식 LOW.
  영향: 6.2.0~6.2.18, 해당 건 수정: 6.2.19. 현재 6.2.12는 영향 범위.
  공격자가 매칭 pattern을 공급해야 한다. 현재 코드에서 해당 입력 경로는 발견하지 못했다.
  https://spring.io/security/cve-2026-41848/
- CVE-2026-24880 / Tomcat HTTP/1.1 chunk extension request smuggling / 공식 Low.
  영향: 10.1.0-M1~10.1.52, 해당 건 수정: 10.1.53. 현재 10.1.48은 영향 범위.
  앞단 reverse proxy가 특정 잘못된 chunk extension을 허용해야 한다. 현재 제공 설정에는 reverse proxy가 없다.
- CVE-2025-66614 / Tomcat client certificate bypass / 공식 Moderate.
  영향: 10.1.0-M1~10.1.49. 여러 TLS virtual host의 인증 정책 차이가 전제.
  현재 TLS/mTLS/virtual host 구성 없음. 초기 10.1.50 수정 후 후속 수정도 있으므로 10.1.50만을 업데이트 목표로 삼지 않는다.
  Tomcat 공식 공지는 2026-10-26 확인 시 10.1.61까지의 수정 사항을 제공한다.
  https://tomcat.apache.org/security-10.html

## 코드/설정 보완점
- application.yml에 server.address 없음. 과거 jar 실행은 명령행에서 127.0.0.1로 제한했지만 ./mvnw spring-boot:run 기본 실행은 같은 제한을 보장하지 않는다.
  실제 외부 도달 가능성은 OS 방화벽/네트워크에 좌우된다. demo 프로필에만 loopback 주소를 지정하고 Docker는 컨테이너 내부 리스닝을 유지하는 방식 권장.
- 공개 검색에 요청 빈도 제한/명시적 DB 쿼리 시간 제한이 없다. 부분 문자열 COUNT/OFFSET 반복 요청은 데이터 증가 시 가용성 위험.
  실제 DoS를 입증한 것은 아니다. 공개 배포 전에 제한과 부하 측정이 필요.
- CSP, frame-ancestors 등 브라우저 보안 헤더 설정이 없다. 방어 보완점이며 XSS가 확인됐다는 의미는 아니다.

## 긍정적 확인
- SQL 값은 named parameter 바인딩, ORDER BY는 허용 목록.
- 사용자 검색어의 LIKE 와일드카드 처리 및 검색어 길이 제한 있음.
- 템플릿 th:text, JS textContent 사용. 확인한 코드에 th:utext/innerHTML 실행 경로 없음.
- .env는 gitignore/dockerignore에 포함. 비밀값을 출력하거나 외부 전송하지 않음.
- Compose 앱 포트는 127.0.0.1에만 게시, DB/Redis 호스트 포트 미게시.
- Docker 앱 USER는 비-root. Actuator HTTP 노출은 health만 지정.
- 현재 읽기 전용 공개 조회 서비스에 인증 미구현 자체를 취약점으로 판정하지 않음.

## 다음 검증
JDK 21 패치 → Boot 호환 패치 업그레이드 → 테스트 → 실제 JAR 전체 SCA 및 Docker 이미지 스캔 → 외부 배포 설정 검토.
