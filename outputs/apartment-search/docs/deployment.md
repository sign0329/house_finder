# 기본 서버 배포

서버 한 대에 Caddy + 앱 + PostgreSQL을 실행한다. 로컬 `compose.yaml`/8082와 별개의 `compose.production.yaml`을 사용한다. 2026-10-29 AWS 서버에서 무료 DNS 주소를 통한 HTTPS 공개를 확인했다. 아래는 재현 및 운영 절차다. 대용량 최적화, Kubernetes, WAF, 별도 모니터링 서버는 포함하지 않는다.

## 서버 준비 후 실행

1. Linux 서버에 Docker Engine과 Compose v2를 설치한다. DNS A/AAAA를 실제 서버 주소에 맞춘다. IPv6를 쓰지 않으면 잘못된 AAAA 레코드는 만들지 않는다.
2. 클라우드 방화벽은 웹 80/443만 공개하고, SSH는 관리자 IP로 제한한다. 5432/8080/8082는 열지 않는다.
3. 저장소의 검증된 main을 내려받고 `outputs/apartment-search` 폴더에서 아래 작업을 수행한다.

```sh
umask 077
cp deploy/env.example .env.production
chmod 600 .env.production
# 파일 편집: DOMAIN을 실제 도메인으로, DB_PASSWORD를 길고 임의의 값으로 설정
# 비밀번호 생성이 필요하면: openssl rand -hex 24
sh scripts/production.sh up
sh scripts/production.sh status
```

이미 `.env.production`이 있으면 예제로 덮어쓰지 않는다. 도메인에는 `https://`나 경로를 붙이지 않는다. DOMAIN/DB_PASSWORD가 빠지면 Compose가 실행을 거부한다. 예시 도메인은 반드시 교체한다.

Caddy가 인증서를 발급·갱신하고 HTTP를 HTTPS로 전환한다. 인증서 볼륨을 유지해야 한다. 실도메인 자동 발급은 DNS/80·443 접근이 준비돼야 확인할 수 있다. [공식 자동 HTTPS 안내](https://caddyserver.com/docs/automatic-https)를 따른다.

## 외부 접근과 인증키

- 호스트에 공개하는 포트는 프록시의 80/443뿐이다. 앱과 DB에는 `ports`를 설정하지 않는다. Docker 기본 네트워크 내부 연결은 허용한다.
- 프록시에서 `/actuator` 경로를 외부에 노출하지 않는다. 내부 컨테이너 헬스체크는 유지한다.
- 배포에서만 Tomcat native forwarded 처리로 HTTPS와 실제 접속 IP를 인식한다. 앱을 직접 공개하거나 다른 프록시를 추가하면 신뢰 범위를 다시 검토한다. 현재 구성은 Caddy가 직접 인터넷 요청을 받는 구조다.
- `.env.production`은 Git과 Docker 빌드에서 제외하고 파일 권한을 600으로 유지한다. API 키는 수집 컨테이너에만 주입하며 웹 앱과 Caddy에는 주입하지 않는다. 환경변수는 Docker 관리자에게는 보이므로 서버/Docker 접근 권한도 제한한다.
- 수집할 때만 키·지역·기간을 설정하고 `sh scripts/production.sh import`를 실행한다. 자세한 옵션은 [공공 API 안내](public-api.md)를 따른다.
- 기존 PostgreSQL 볼륨의 비밀번호는 환경파일만 바꿔도 변경되지 않는다. 비밀번호 교체는 DB 역할 비밀번호와 환경파일을 함께 변경해야 한다.

## 백업

```sh
sh scripts/production.sh backup
```

`backups/apartments-날짜-프로세스번호.dump`를 생성한다. 임시 파일에 덤프가 완성된 뒤 최종 이름으로 옮기며 디렉터리는 700, 파일은 600이다. 실패하면 완료 파일을 만들지 않는다. 백업은 Git과 이미지에서 제외한다.

실제 배포 후 서버의 `crontab -e`에 아래 예시를 등록하면 매일 새벽 3시에 실행된다. 경로와 Docker 실행 권한을 서버에 맞춘다. 지금 자동 작업을 등록한 상태는 아니다.

```cron
0 3 * * * /bin/sh /srv/house_finder/outputs/apartment-search/scripts/production.sh backup >> /srv/house_finder/outputs/apartment-search/backups/backup.log 2>&1
```

먼저 백업 명령을 한 번 실행해 폴더를 만든다. 실행 실패 로그를 확인하고, 적어도 최근 7일 백업을 별도 저장소나 개인 PC에 복사한다. 같은 서버의 백업만으로는 서버 손실에 대비할 수 없다. 자동 삭제는 하지 않으므로 오래된 백업은 복사·확인 후 정리한다.

복원은 기존 데이터를 바꾸므로 앱을 중지하고 백업 파일과 대상 서버를 확인한 뒤 직접 실행한다:

```sh
docker compose --env-file .env.production -f compose.production.yaml stop app
docker compose --env-file .env.production -f compose.production.yaml exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --exit-on-error' < backups/선택한파일.dump
# 복원 성공을 확인한 뒤 재시작
docker compose --env-file .env.production -f compose.production.yaml up -d --wait app
```

복원 실패 시 앱을 올리지 말고 오류를 확인한다. `down --volumes`는 운영 DB와 인증서 볼륨을 삭제하므로 사용하지 않는다.

현재 로컬의 종로구·청주 거래는 서버에 자동 복사되지 않는다. 배포 시 로컬 DB를 pg_dump로 백업해 안전하게 전송하고, 위 절차로 새 서버 DB에 복원하거나 서버에서 다시 수집한다. 인증키·덤프를 Git에 올리지 않는다.

## 검증 (2026-10-27)

환경 설정 검증은 핵심 3개로 제한했다. 기존 8082 DB를 사용하지 않고 `house-deployment-check` 임시 프로젝트에서 확인했다.

1. Compose 구성: DB/앱 호스트 포트 없음, API 키는 importer에만 있음, 빈 DB 비밀번호 거부.
2. Caddy 설정 검증 및 로컬 CA를 신뢰한 TLS 접속 성공(`-k` 사용 안 함), HTTP→HTTPS 308, 검색 프록시 정상, 외부 health 경로 404. 실제 도메인 공인 인증서 발급은 서버·DNS 마련 후 확인해야 한다.
3. 백업 스크립트 실행, 파일 권한 600, 별도 임시 PostgreSQL DB에 복원 후 검증용 행 값 일치. 테스트 컨테이너·볼륨만 정리했다.

Java 코드는 변경하지 않았다. 기존 필수 GitHub CI는 그대로 유지한다. 위 검증 당시에는 공개 배포 전이었다. 현재 공개 상태는 아래 완료 기록을 따른다. 정기 백업 예약과 서버 외부 백업 운영은 별도 후속 작업이다.

## HTTPS 공개 완료 (2026-10-29)

- URL: https://3-107-167-57.sslip.io/
- AWS에서 앱·PostgreSQL·Caddy를 재시작하고 모두 healthy 상태를 확인했다.
- 외부 HTTP 요청은 HTTPS로 308 전환된다. TLS 검증을 생략하지 않은 curl 요청으로 웹 화면과 검색 API를 확인했다.
- `/api/trades` 결과는 총 726건(종로구 32건, 청주 694건)이다. `/actuator/health` 외부 요청은 404다.
- DB 및 앱 포트는 호스트에 직접 공개하지 않고 프록시의 80/443만 공개한다. 키 파일·DB 비밀번호·API 키·DB 덤프는 Git에 포함하지 않는다.
- 무료 IP 기반 DNS 주소를 사용한다. EC2 공인 IP 변경 시 DOMAIN 설정과 안내 링크를 갱신해야 한다. 사이트 운영 여부는 서버 실행 상태에 따라 달라진다.
