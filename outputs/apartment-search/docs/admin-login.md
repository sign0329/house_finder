# 관리자 로그인

관리자 1개 계정을 환경 설정으로 제공한다. 회원 가입/일반 사용자 로그인/권한 관리/비밀번호 재설정 이메일은 범위에 없다. DB 스키마 변경 없음.

## 설정

1. 앱을 빌드한다: `sh ./mvnw package`
2. 터미널에서 `sh scripts/admin-password.sh`를 실행한다. 비밀번호는 화면에 표시되지 않으며 12자 이상, UTF-8 72바이트 이하를 두 번 입력한다. BCrypt 비용 12 해시만 출력한다. JAVA_HOME 또는 PATH에 Java 21 이상이 필요하다.
3. IntelliJ 실행 환경 변수 또는 비공개 환경 파일에 설정한다.

```dotenv
COLLECTION_API_ENABLED=true
ADMIN_USERNAME=운영용아이디
ADMIN_PASSWORD_HASH='생성한 BCrypt 해시'
```

IntelliJ 환경 변수 편집기에는 해시 자체를 입력하고 따옴표를 넣지 않는다. Compose 환경 파일에서는 `$` 보간을 막도록 해시를 작은따옴표로 감싼다. 비밀번호/해시/환경 파일은 Git에 올리지 않는다. 설정이 누락되거나 해시 형식이 잘못되면 관리 기능을 켠 앱은 시작하지 않는다. 임의 기본 계정은 없다.

앱 재시작 후 `/admin/login`에서 입력한 원래 비밀번호로 로그인한다. 서버에는 해시만 제공한다. 비밀번호 변경은 새 해시로 설정 후 앱을 재시작한다. 계정은 메모리에 구성되지만 환경 설정이 유지되므로 재시작 후에도 동일 계정으로 로그인할 수 있다. 로그인 세션은 재시작 후 다시 발급받는다.

Compose 앱 서비스에 활성화·아이디·해시 설정을 전달하도록 구성했다. production Compose는 Secure 쿠키를 사용하므로 HTTPS로 접속한다. 로컬 Compose는 HTTP를 위해 Secure=false다. 직접 HTTPS 배포 시 ADMIN_COOKIE_SECURE=true를 설정한다. 브라우저에는 HttpOnly, SameSite=Strict 쿠키를 사용하며 세션 유휴 만료 시간은 30분이다.

기본 Compose 앱은 공공 API 키를 전달하지 않아 로그인과 조회만 가능하다. 웹 수집까지 사용할 경우 서버 환경에 MOLIT_SERVICE_KEY를 명시적으로 전달해야 한다. 기존 importer는 별도 실행하며 인증 설정을 요구하지 않는다.

## 인증과 요청 보호

Spring Security 폼 로그인을 사용한다. 성공하면 세션 ID를 변경하고, 로그아웃은 CSRF 검증을 통과한 POST만 처리하며 세션을 폐기한다. 세션으로 실행하는 수집·재시도에도 CSRF 헤더가 필요하다. 만료된 세션의 API 조회는 401, CSRF 오류는 403이다. 검색·상세·헬스체크는 로그인 없이 접근한다.

로그인 POST는 실제 연결 IP당 분당 10회, 프로세스 전체 분당 1,000회로 제한한다. 성공/실패 모두 포함하고 고정 1분 구간·메모리 방식이다. 서버 재시작 시 초기화되며 다중 서버 간 공유되지 않는다. 프록시 뒤에서는 프록시 주소가 함께 집계될 수 있다. 클라이언트가 보낸 전달 헤더를 임의로 신뢰하지 않는다.

자동화용 COLLECTION_ADMIN_TOKEN은 선택 사항(설정 시 32자 이상)이다. X-Collection-Token 헤더가 있는 API 요청은 세션을 읽지 않는 별도 체인에서 토큰만 검증한다. 따라서 잘못된 토큰을 보내 CSRF를 우회해 로그인 세션을 이용할 수 없다. 브라우저는 관리 토큰을 보관하거나 보내지 않는다. 기존 토큰 자동화를 사용하는 서버는 토큰 설정을 유지할 수 있다.

## 검증

2026-11-04: 새 로그인 테스트 5개(공개 화면/인증 경계, 잘못된 비밀번호, 세션 ID 변경·로그아웃, CSRF·잘못된 토큰 우회 차단, 로그인 요청 제한). 구현 전 로그인/리다이렉트 테스트 4개 실패를 확인했다. 최종 clean verify -Ppostgres-it: 기본 66개 + PostgreSQL 14개 통과. 기존 토큰 API 테스트도 유지했다. 해시 도구를 터미널에서 실행했고 브라우저 로그인→목록 조회→로그아웃을 확인했다. 실제 공공 API 호출·AWS 배포 없음.

설계 참고: [Spring Security Form Login](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/form.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).
