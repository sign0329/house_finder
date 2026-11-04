# 사용자 로그인 AWS 배포 — 2026-11-04

- 사이트: https://3-107-167-57.sslip.io/
- 회원가입: /signup, 로그인: /login, 내 계정: /account
- 배포 코드 edab95b, 이미지 house-finder-app:release-edab95b (linux/amd64)
- Compose: /home/ubuntu/house_finder/releases/edab95b/outputs/apartment-search/compose.production.yaml
- 환경 파일: /home/ubuntu/house_finder/outputs/apartment-search/.env.production
- 기존 house-finder-production 프로젝트와 DB 볼륨 유지

## 검증

앱/DB/프록시 정상 기동. Flyway V1~V7 성공, user_account 테이블 생성. 배포 직후 계정 0개이며 기본 사용자를 넣지 않았다. 기존 거래 31,954건 유지.

외부 HTTPS 기준 /, /login, /signup, /api/trades 200. 가입·로그인 폼 CSRF 필드 및 Secure/HttpOnly 세션 쿠키 확인. 비로그인 /account는 /login으로 302 이동. 운영 계정 가입/로그아웃은 자동 실행하지 않았으며 앱 테스트 86개(기본 71 + PostgreSQL 15)에서 해당 흐름을 검증했다. 브라우저 시각 검증은 앞선 승인 검토 사용량 제한으로 수행하지 않았다.

운영 관리자 자격 증명을 설정하지 않았으므로 기존과 같이 관리 기능은 비활성화(/admin 404). 로컬 임시 관리자 계정을 운영에 복사하지 않았다. 실제 사용자 계정은 운영 PostgreSQL에 저장되며 로컬 미리보기 계정과 별개다.

## 백업과 복구

- 배포 전: /home/ubuntu/house_finder/outputs/apartment-search/backups/apartments-20261104T075341Z-2054183.dump
- 배포 후: /home/ubuntu/house_finder/outputs/apartment-search/backups/apartments-20261104T075613Z-2055992.dump
- 이전 이미지 태그: house-finder-app:rollback-before-edab95b
- 이전 환경 파일: /home/ubuntu/house_finder/outputs/apartment-search/.env.production.before-edab95b

기동 실패 시 이전 앱으로 복귀하도록 배포했으며 실제 복귀는 필요하지 않았다. V7은 추가 테이블 마이그레이션이다. 이후 앱만 이전 이미지로 되돌리면 사용자 화면이 없어지지만 테이블은 유지된다. DB 복원은 이후 가입 계정을 잃을 수 있으므로 자동으로 수행하지 않는다.

운영 명령은 새 Compose 경로와 기존 환경 파일을 함께 지정한다. 검증된 이미지를 사용하려면 --build 없이 up -d --no-build --wait app proxy로 실행한다. 비밀번호/해시/환경 파일/DB 백업은 Git에 포함하지 않는다.
