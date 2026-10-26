# 앱 + PostgreSQL 전체 컨테이너 실행

## 이 컴퓨터에서 바로 사용

Docker Desktop을 켜고 `pom.xml`이 있는 프로젝트 폴더에서:

```sh
sh scripts/stack.sh up
```

접속: http://localhost:8082

Docker Desktop → Containers → **house-finder-stack**을 펼치면 `app`과 `db`가 보인다. 모두 실행 중이면 웹에 접속할 수 있다. 기존 `house-finder-postgres`는 IntelliJ용 별도 DB다.

## 새 컴퓨터의 최초 설정

```sh
cp .env.example .env.docker
# .env.docker의 DB_PASSWORD를 변경한다.
# 실제 수집을 하려면 MOLIT_SERVICE_KEY에 발급받은 키를 입력한다.
sh scripts/stack.sh up
sh scripts/stack.sh import
```

로컬 `.env.docker`는 Git 및 Docker 빌드에서 제외된다. 키·비밀번호를 GitHub에 올리거나 Docker 이미지에 넣지 않는다. 일반 앱은 API 키를 받지 않고, 수집 명령으로 실행한 일회성 importer만 키를 받는다. 기본 `DEMO_DATA=false`에서 수집 전 화면은 0건이다. 가상 체험은 별도 DB에서 `DEMO_DATA=true`로 실행하며, 가상 데이터가 들어간 DB에는 실제 수집을 허용하지 않는다.

## 명령

| 명령 | 동작 |
| --- | --- |
| `sh scripts/stack.sh up` | 이미지 빌드 및 앱·DB 시작, healthy까지 대기 |
| `sh scripts/stack.sh import` | 종로구의 지정 월 수집 후 importer 종료·제거 |
| `sh scripts/stack.sh status` | 컨테이너 상태 |
| `sh scripts/stack.sh logs` | 앱·DB 최근 로그 |
| `sh scripts/stack.sh restart` | 앱 재시작 후 준비 상태 확인 |
| `sh scripts/stack.sh stop` | 앱·DB 정지, 컨테이너와 볼륨 유지 |
| `sh scripts/stack.sh down` | 컨테이너·네트워크 제거, DB 볼륨 유지 |

월을 바꾸려면 `.env.docker`의 `MOLIT_MONTH=202609`를 수정하고 import를 실행한다. key가 없거나 오류가 나면 수집은 실패하고 기존 저장 데이터는 보존된다. import 전에 up으로 이미지를 빌드해 둬야 한다. 실제 공공 API에 부하 테스트를 하지 않는다.

포트 변경은 `.env.docker`의 `PORT`를 수정하고 up을 실행한다. 호스트 포트만 바뀌고 앱 컨테이너 내부는 항상 8080, DB 내부는 5432다. DB 포트는 호스트에 공개하지 않는다.

## 데이터와 실행 방식

```text
브라우저 localhost:8082 → app 컨테이너:8080 → db 컨테이너:5432
                                             ↓
                                house-finder-stack_pgdata 볼륨
```

기존 H2 및 IntelliJ용 PostgreSQL과 별개 DB다. 처음에는 공공 API로 다시 수집하며, 다른 DB의 파일을 덮어쓰거나 자동 이동하지 않는다. 재시작·down 후 up에도 볼륨은 유지된다. **down -v 또는 Docker Desktop에서 볼륨 삭제는 저장 데이터를 삭제한다.**

서버 컨테이너는 비root 사용자로 실행한다. 이미지 빌드 단계에서 Maven 테스트를 실행하고 런타임에는 JRE와 JAR만 복사한다. curl은 준비 상태 확인용이다. readiness가 성공해야 Compose가 준비 완료로 판단한다. Java는 공식 Adoptium Linux JDK/JRE 21.0.13+9 아카이브를 `docker/java21-checksums.txt`의 SHA-256으로 확인하여 설치한다(ARM64/AMD64 지원). Maven 이미지에서는 Maven 도구만 가져온다. 기본 OS·PostgreSQL 이미지 태그는 바뀔 수 있으므로 장기 운영에는 검증한 digest와 업데이트 정책이 추가로 필요하다.

## IntelliJ 개발과의 관계

- 8080: 기존 H2 서버(실행 중인 경우)
- 8081: 기존 맥 Java 앱 + PostgreSQL 개발 DB
- 8082: 이번 전체 Docker 앱 + 독립 PostgreSQL DB

기존 서버는 자동 종료하지 않는다. 앞으로 Docker만 사용할지 결정한 뒤 불필요한 서버를 종료하면 된다. 평소 디버깅에는 IntelliJ 방식도 계속 사용할 수 있다.

## CI

GitHub의 Java tests에서 합성 데이터로 이미지 빌드, Compose 앱/DB 준비 상태, 검색 240건, 앱 재시작 후 240건 유지를 확인한다. 작업 종료 후 CI 전용 볼륨을 정리한다. 실제 API 키는 CI에 제공하지 않는다. 로컬 실제 수집 결과는 [검증 기록](verification.md)에 별도로 남긴다.
