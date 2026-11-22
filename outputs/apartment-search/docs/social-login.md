# 카카오·구글 로그인 설정

기본 환경에서는 소셜 로그인 버튼을 표시하지 않음. 기존 아이디·비밀번호 로그인은 유지됨. 각 제공자의 설정을 완료한 뒤 독립적으로 활성화함.

## 로컬 8082 설정

1. Google Cloud에서 OAuth 동의 화면(대상 사용자·앱 정보) 설정 후 웹 애플리케이션 유형 OAuth 클라이언트를 생성함. 테스트 모드라면 사용할 Google 계정을 테스트 사용자로 추가함.
2. 카카오 디벨로퍼스에서 앱을 생성하고 카카오 로그인을 활성화함. REST API 키와 Client Secret을 사용함. 지도용 JavaScript 키와 다름. 본 구현은 카카오 OIDC가 아닌 OAuth2 사용자 정보 API를 사용함.
3. 아래 Redirect URI를 해당 제공자에 정확히 등록함. localhost와 127.0.0.1은 서로 다른 주소임.

| 제공자 | 로컬 Redirect URI |
| --- | --- |
| Google | `http://localhost:8082/login/oauth2/code/google` |
| 카카오 | `http://localhost:8082/login/oauth2/code/kakao` |

4. 실행에 사용하는 비공개 env 파일에 아래 값을 입력함. 키·시크릿은 Git/채팅에 올리지 않음.

```dotenv
SOCIAL_LOGIN_BASE_URL=http://localhost:8082
GOOGLE_LOGIN_ENABLED=true
GOOGLE_CLIENT_ID=발급한_Client_ID
GOOGLE_CLIENT_SECRET=발급한_Client_Secret
KAKAO_LOGIN_ENABLED=true
KAKAO_CLIENT_ID=발급한_REST_API_키
KAKAO_CLIENT_SECRET=발급한_Client_Secret
```

한쪽만 준비했다면 나머지의 ENABLED는 false로 유지함. 활성화했는데 키가 비어 있으면 설정 오류로 부팅을 중단함.

5. 프로젝트 폴더에서 해당 env 파일을 명시하여 앱을 재생성함.

```sh
docker compose --env-file <사용중인-env-파일> up -d --build --no-deps app
```

6. `/login`에서 버튼 선택 → 제공자 인증/동의 → 홈 이동 → 마이페이지·관심 목록 확인 순서로 검증함. 인증을 취소하면 오류 안내 후 다시 시도할 수 있음.

## 운영 설정

`SOCIAL_LOGIN_BASE_URL`을 실제 HTTPS origin으로 변경하고 두 제공자의 Redirect URI도 `https://사이트주소/login/oauth2/code/google`, `https://사이트주소/login/oauth2/code/kakao`로 추가함. 예시 localhost 값을 운영에 그대로 사용하면 안 됨. HTTPS·Secure 쿠키·외부 주소 설정을 함께 확인함. 외부 인증 화면에서 돌아오는 최상위 GET 요청에 세션 쿠키를 전달하도록 SameSite=Lax를 사용하며 CSRF 보호는 유지함.

## 계정·보안 정책

- Google은 OIDC로 서명·issuer·audience·nonce를 Spring Security에서 검증함. 카카오는 서버에서 인가 코드를 교환하고 사용자 ID를 조회함. OAuth state 검증과 세션 고정 방지를 사용함.
- V16 `social_identity`의 `(provider,subject)`로 식별함. 이메일·닉네임으로 기존 회원을 자동 연결하지 않음. 처음 접속하면 별도 회원 계정이 생성되며 다음 접속부터 재사용함.
- 최초 가입은 짧은 DB 잠금과 유일 제약으로 직렬화하여 동시에 로그인해도 중복 계정을 만들지 않음.
- 소셜 계정은 USER 권한만 부여함. 관리자 인증은 별도임. 소셜 계정에는 비밀번호 로그인을 허용하지 않음.
- DB에는 제공자·고유 ID·내부 회원 ID만 연결 저장함. 이메일·제공자 프로필 사진을 가져와 저장하지 않음. Google scope는 openid/profile, 카카오는 추가 프로필 동의 없이 ID만 사용함.
- OAuth 토큰을 DB에 저장하지 않음. 단일 앱의 메모리 authorized-client 저장소를 사용함.
- 로그아웃은 집결 세션 종료임. Google/카카오 전체 로그아웃이나 연결 해제는 포함하지 않음. 기존 계정 연결·계정 탈퇴는 별도 기능임.

## 검증과 한계

키 없이 기본 부팅, 설정된 버튼, 실제 필터를 통과하는 모의 인가 코드 교환, Google 모의 서명 ID 토큰 검증, 잘못된 state/nonce 차단, 기존 로그인, 사용자 세션과 마이페이지 연결, 동시 최초 가입을 자동 검증함. 실제 제공자 앱 등록·동의 화면·운영 Redirect URI·실제 로그인은 키 발급 후 확인해야 함.

공식 문서:
- [카카오 로그인 REST API](https://developers.kakao.com/docs/ko/kakaologin/rest-api)
- [Google OpenID Connect](https://developers.google.com/identity/openid-connect/reference)
- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/login/advanced.html)
