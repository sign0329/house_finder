# 개발 및 브랜치 전략

이 저장소는 GitHub Flow를 사용한다. `main`은 검증된 기준 브랜치이며, 작업은 짧게 유지하는 별도 브랜치에서 시작한다. `develop`이나 장기 release 브랜치는 두지 않는다.

## 브랜치 이름

| 접두사 | 목적 | 예시 |
| --- | --- | --- |
| feat/ | 새 기능 | feat/molit-regions |
| fix/ | 오류 수정 | fix/molit-key-encoding |
| docs/ | 문서 | docs/api-guide |
| perf/ | 측정 기반 성능 개선 | perf/search-index |
| chore/ | CI·설정 정리 | chore/github-flow |

이름은 팀 규칙이며 서버에서 접두사를 강제하지는 않는다.

## 작업 순서

```sh
# 기존 작업을 커밋하거나 보관한 뒤 실행
git switch main
git pull --ff-only origin main
git switch -c feat/molit-regions

# 구현 후 프로젝트 폴더에서 실행
cd outputs/apartment-search
sh ./mvnw verify
cd ../..

# 변경 파일만 명시해서 추가
git add <변경한-파일>
git commit -m "feat: 실거래가 수집 지역 선택 지원"
git push -u origin feat/molit-regions
```

GitHub에서 작업 브랜치 → main PR을 만든다. PR 템플릿에 변경 이유, 실제 실행한 검증, 남은 제한을 적는다. 기능은 feat, 오류 수정은 fix, 문서는 docs, 설정은 chore로 커밋 제목을 시작한다. 커밋 제목 형식 역시 수동 규칙이다.

## 병합 정책

- main 변경은 PR로 병합한다. 관리자에게도 보호 규칙을 적용한다.
- 필수 상태 검사: **Java tests**. main의 최신 변경을 반영한 상태여야 한다.
- 대화 스레드를 해결한 뒤 병합한다.
- 개인 프로젝트이므로 다른 사람의 승인 인원은 0명이다. 테스트와 직접 검토는 생략하지 않는다.
- **Squash and merge**로 작업을 한 커밋으로 합친다. Merge commit/Rebase merge는 사용하지 않는다.
- 병합한 원격 작업 브랜치는 자동 삭제한다. main 강제 푸시와 삭제는 금지한다.

원격 브랜치 삭제 후 로컬은 자동 정리되지 않는다. main을 갱신하고 파일 변경이 반영됐는지 확인한 뒤 작업 브랜치를 삭제한다. Squash 병합은 로컬 `git branch -d`가 미병합으로 판단할 수 있으므로 무조건 강제 삭제하지 않는다.

## CI 범위

`.github/workflows/ci.yml`은 main 대상 PR과 main 푸시마다 Ubuntu + Temurin 21에서 Maven clean verify를 실행한다. 경로 필터를 두지 않아 문서 PR도 필수 검사가 생성된다. 공공 API 키를 CI에 넣지 않으며 테스트는 H2/합성 XML/모의 HTTP를 사용한다. PostgreSQL·Docker·실제 API 호출까지 보장하지 않는다.

Actions는 읽기 권한으로 실행하며 외부 액션은 커밋 SHA로 고정한다. Jenkinsfile은 별도 준비 설정이고 GitHub 필수 검사는 GitHub Actions가 담당한다.

프로젝트는 저장소 루트가 아닌 `outputs/apartment-search`에 있다. 상세 실행 방법은 [프로젝트 README](outputs/apartment-search/README.md)를 따른다. 최초 업로드 당시 포함된 work·IDE·ZIP 파일은 기존 이력에 남아 있으며, 루트 .gitignore가 기존 추적을 해제하지는 않는다. 새 PR에 개인 파일 변경을 포함하지 않는다.
