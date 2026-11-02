# AWS 최신 기능·데이터 반영 (2026-11-02)

- URL: https://3-107-167-57.sslip.io/
- 배포 코드: 0ae605c (수집 관리 기능 포함). 이미지: house-finder-app:release-0ae605c, linux/amd64.
- 소스/Compose: /home/ubuntu/house_finder/releases/0ae605c/outputs/apartment-search
- 환경 파일: /home/ubuntu/house_finder/outputs/apartment-search/.env.production
- Compose project: house-finder-production. 기존 PostgreSQL 볼륨 유지.

## 검증 결과

공개 HTTPS / 및 거래 상세는 200, /api/trades의 total은 31,954. 기존 726건의 (id, source_key) 쌍이 전부 보존됐다. source_key가 같은 거래는 갱신하고 없는 거래만 추가했으며 단일 트랜잭션으로 적용했다. 로컬 임시 복원 DB에서 동기화 SQL과 ID 보존을 먼저 검증했다.

Flyway SQL V1~V6 성공 확인. collection_job 0건은 정상이다. 이번 데이터 복사는 과거 수집 이력을 만들지 않으며 앞으로 새 importer/관리 기능을 사용할 때 기록된다. 관리 API는 기본 비활성화 상태이고 외부 요청은 404다. 웹 앱에 공공 API 키/관리 토큰을 추가하지 않았다.

이번 배포는 전국 전체 데이터 적재가 아니다. 저장된 서울 일부·청주 중심 거래를 반영한 것이다.

## 운영 명령

```sh
cd /home/ubuntu/house_finder/releases/0ae605c/outputs/apartment-search
sudo docker compose --env-file /home/ubuntu/house_finder/outputs/apartment-search/.env.production -f compose.production.yaml ps
# 재시작 시 --build 없이 검증된 이미지를 사용
sudo docker compose --env-file /home/ubuntu/house_finder/outputs/apartment-search/.env.production -f compose.production.yaml up -d --no-build --wait app proxy
```

## 복구 자료

배포 전 DB 백업: /home/ubuntu/house_finder/outputs/apartment-search/backups/apartments-20261102T004607Z-862452.dump

이전 이미지 태그: house-finder-app:rollback-20261102. 이전 환경 파일: .env.production.before-0ae605c (기존 앱 디렉터리). 이미지 태그 변경만으로 DB 마이그레이션이 역행하지 않는다. 이전 코드로 되돌릴 때 V5/V6와의 호환을 확인해야 하며, 전체 DB 복원은 배포 이후 데이터를 잃을 수 있으므로 운영 중 자동으로 수행하지 않는다.

DB dump/동기화 SQL/환경 파일은 Git에 포함하지 않았다. 원본 IDE 폴더와 별도 로컬 데이터베이스는 변경하지 않았다.
