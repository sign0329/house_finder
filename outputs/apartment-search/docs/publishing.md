# GitHub 첫 업로드 안내

이 프로젝트 폴더 자체를 저장소 루트로 사용한다. 상위 작업 폴더나 outputs 전체를 업로드하지 않는다.

## 포함 / 제외

포함: src, pom.xml, mvnw, scripts, docs, load-tests, Dockerfile, compose.yaml, Jenkinsfile, .env.example, README, AGENTS, preview.jpg.

제외: .env 및 실제 키, .tools, .m2, target, IDE 개인 설정, 로그, 소스 ZIP, 부하 테스트 원본 산출물. .gitignore가 실수 가능성을 낮추지만 이미 추적한 비밀을 제거하지는 못한다.

## 첫 커밋

이 문서는 명령 안내이며 원격 저장소 생성·push를 자동으로 수행하지 않는다.

```sh
# README.md와 pom.xml이 있는 프로젝트 폴더에서 실행
git init -b main
git add .
git status --short
git diff --cached --stat
git diff --cached
# 실제 비밀 값과 개인 파일이 없는지 검토한 뒤
git commit -m "feat: 아파트 거래 검색 웹 MVP 구성"
```

GitHub에서 빈 저장소를 만들고, 표시된 본인의 원격 주소로 아래 명령을 실행한다. 아래 YOUR_ACCOUNT는 실제 계정으로 교체한다.

```sh
git remote add origin https://github.com/YOUR_ACCOUNT/apartment-search.git
git push -u origin main
```

이미 저장소가 있다면 init이나 remote 추가를 반복하지 말고 `git status`, `git remote -v`로 먼저 확인한다. 과거 작업을 시간순으로 했던 것처럼 커밋을 꾸미지 않고 현재 상태를 첫 커밋으로 남긴다.

## 공개 전 확인

- [ ] stage 목록에 .env, 인증 키, 캐시, 빌드 결과가 없다.
- [ ] README의 가상 데이터 표시와 미검증 범위가 실제 상태와 일치한다.
- [ ] 새 폴더에 클론한 뒤 README의 JDK 설정·실행·테스트를 따라 확인했다.
- [ ] GitHub에서 이미지·상대 링크·Mermaid가 정상 표시된다.

저장소 설명 예시: `Java/Spring Boot 기반 아파트 거래 검색 웹 MVP — Thymeleaf, JDBC, Flyway, 테스트 및 보안 검증`.

추천 토픽: java, spring-boot, thymeleaf, jdbc, flyway, portfolio. 라이선스는 소유자가 공개·재사용 조건을 결정한 뒤 추가한다.
