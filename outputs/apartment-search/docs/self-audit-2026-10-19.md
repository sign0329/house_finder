# 자가 점검 — 2026-10-19

## 판단
웹 데모와 개발 환경 파일을 갖춘 초기 MVP. 공공 실거래 데이터와 대용량 성능을 검증한 포트폴리오는 아직 아님.

## 현재 구현
- Spring Boot/Thymeleaf 검색, 조건 필터, 정렬, 12건 OFFSET 페이지네이션.
- 거래 상세, 같은 주소·단지·면적의 최근 거래 표와 차트.
- localStorage 관심 저장. 계정/서버 저장은 아님.
- 고정 시드의 가상 거래 240건, H2 데모, Flyway 테이블/인덱스.
- PostgreSQL 프로필, Dockerfile/Compose, 선택적 Redis 컨테이너, Jenkins 빌드 단계.
- AGENTS.md 및 요구사항/구조 문서.

## 이번 검증
- 기본 `./mvnw -o -q verify`는 Mockito 자동 attach 문제로 실패.
- 아래 명시적 agent 옵션으로 verify 재실행: 종료 코드 0, 8 tests / 0 failures / 0 errors / 0 skipped.

```sh
./mvnw -o -q '-DargLine=-javaagent:.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' verify
```

- 화면 JS, k6 JS 구문 검사와 mvnw 셸 구문 검사 통과.
- docker/k6 명령을 현재 PATH에서 찾지 못함. 컨테이너, PostgreSQL, 부하 실험은 미검증.
- 8080 포트 리스너 없음. 서버를 시작하지 않았음.
- 브라우저 검증은 10월 13일 기록만 존재. 이번에는 코드/자동 테스트 점검.

## 개선할 항목
1. 테스트 실행 재현성: Mockito agent 명시 설정을 빌드 설정에 반영할 필요.
2. 테스트 범위: inclusivePriceBoundary는 0원 결과 없음만 검사한다. 존재하는 가격 경계 포함 여부를 입증하지 못함. 날짜·면적 경계, 정렬 순서, 페이지 간 중복/누락, PostgreSQL 테스트도 필요.
3. API 오류 형식: /api/trades 성공은 JSON, IllegalArgumentException 오류는 HTML 뷰. API 오류 응답 표준화 필요.
4. 데이터 모델: 같은 단지의 건축연도를 샘플 행마다 다르게 생성함. 단지 식별/정보를 거래와 분리하고 안정적인 식별자를 마련할 필요.
5. 관심 저장 링크가 거래 ID에 의존하므로 해당 거래 삭제 시 깨질 수 있음.
6. 성능: COUNT/OFFSET 및 부분 문자열 검색은 초기 기준선. 실제 규모에서 실행 계획/측정 없이 대용량 처리 완료라 할 수 없음.
7. k6: 기본 5VU/30초 스모크 스크립트이며 checks 임계값이 없어 응답 내용 검증 실패만으로 실행이 실패하지 않을 수 있음.
8. TDD: 테스트 작성과 통과는 확인했지만 최초 Red 실행 기록 없음. 전체 TDD 수행을 주장하면 안 됨.

## 미구현
공공 API 수집, 중복/정정/취소 처리, Spring Batch 재시작, Testcontainers,
대량 적재/성능 개선 전후 수치, Redis 앱 연동, Jenkins 실제 실행/배포, AWS 배포.

## 다음 순서
테스트 실행 설정 및 핵심 경계 검증 보완 → PostgreSQL/Compose/Testcontainers 검증 →
한 지역·한 달 API 샘플 확인 및 데이터 모델 → 수집 TDD → 대량 적재/기준 성능 → 최적화 → 배포.

점검 중 애플리케이션 코드나 설정을 수정하지 않았으며 이 보고서만 추가함.
