# 문서 안내

## 실행과 운영
- [개발 환경](development.md)
- [Docker 실행](docker.md)
- [PostgreSQL](postgresql.md)
- [배포](deployment.md)

## 기능
- [공공 API](public-api.md)
- [전국 데이터 수집](nationwide-collection.md)
- [수집 작업 관리](collection-jobs.md)
- [사용자 로그인](user-login.md)
- [관리자 로그인](admin-login.md)
- [관심 아파트](bookmarks.md)
- [지도](maps.md)

## 설계와 기록
- [아키텍처](architecture.md)
- [요구사항](requirements.md)
- [검증 기록](verification.md)
- `decisions/`: 설계 결정
- `reports/`: 날짜가 명시된 과거 보안·자체 점검 결과. 현재 상태와 구분해서 읽습니다.
- `deployment-*.md`: 배포 시점별 기록

## 폴더 관리 기준

| 위치 | 역할 |
| --- | --- |
| `src/main/java/kr/aptfolio/` | account·bookmark·collection·trade 등 기능별 Java 코드 |
| `src/main/resources/` | 템플릿·정적 파일·설정·DB 마이그레이션 |
| `src/test/` | 테스트 코드와 fixture |
| `config/` | 지역 코드 등 버전 관리 설정 |
| `scripts/` | 실행·수집·검증 도구 |
| `docker/`, `deploy/` | 이미지 빌드와 서버 배포 설정 |
| `load-tests/` | 부하 테스트 스크립트 |
| `target/`, `.tools/`, `.m2/` | 로컬 빌드 결과·도구·캐시, Git 제외 |
| `data/`, `backups/`, `.env*` | 로컬 데이터·백업·환경변수, Git 제외(예제 제외) |

저장소의 앱 경로는 `outputs/apartment-search`로 유지합니다. CI·Docker·IDE가 참조하는 경로는 변경하지 않습니다. 저장소 루트의 `work/`(초기 생성 도구와 로그), `.idea/`, ZIP은 로컬에 보존하고 Git에서만 제외합니다. 사용자가 수정 중인 다른 작업 폴더에 강제 적용하거나 파일을 삭제하지 않습니다.
