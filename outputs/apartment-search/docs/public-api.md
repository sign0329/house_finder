# 국토부 API 연결 — 첫 수집 실행

## IntelliJ에서 실행

이미 설정한 `MOLIT_SERVICE_KEY` 환경 변수(Encoding 또는 Decoding 인증키)는 그대로 유지한다. 키를 공유 실행 설정이나 Git에 저장하지 않는다.

1. Maven 프로젝트를 새로고침한다. JDK는 프로젝트 `.tools/java-home` 또는 패치된 Java 21.
2. Run → Edit Configurations → ApartmentApplication을 선택한다.
3. **Program arguments**에 아래 값을 넣는다. 항목이 없으면 Modify options에서 추가한다. VM options가 아니다.

```text
--spring.profiles.active=molit --molit.import-on-start=true --molit.month=202609
```

4. **Working directory**를 `pom.xml`이 있는 `outputs/apartment-search` 폴더로 설정한다.
5. 같은 포트를 사용 중인 기존 앱을 종료하고 실행한다.
6. 콘솔의 `MOLIT 수집 완료`와 유효 거래 건수를 확인하고 http://localhost:8080 에 접속한다.

수집 대상은 **종로구(11110)**로 고정되어 있다. 계약월은 바꿀 수 있다. 처음에는 202609로 확인한다. 공개 신고 데이터이므로 해당 기간의 결과가 0건일 수도 있다.

수집 이후 검색만 할 때는 Program arguments를 아래처럼 바꾼다. 재시작해도 저장한 데이터는 유지된다.

```text
--spring.profiles.active=molit
```

같은 월을 최신화하려면 `--molit.import-on-start=true`를 다시 지정한다. true를 유지하면 실행할 때마다 외부 호출을 한다. 별도 공개 수집 HTTP API는 제공하지 않는다.

## 저장·오류 처리

- 기본 demo는 기존 메모리 DB, molit은 별도 파일 DB `data/molit.mv.db`를 사용한다. 수집 파일은 Git/Docker 빌드에서 제외한다.
- molit에서는 가상 데이터를 생성하지 않는다. 기존 가상 데이터가 있는 DB에는 저장을 거부한다.
- 지역·월의 모든 페이지를 검증한 다음 트랜잭션으로 반영한다. 총건수·페이지 불일치 또는 파싱 실패 시 저장하지 않는다.
- 최신 응답의 해제 거래를 검색 대상에서 제외한다. 같은 월의 이전 목록에만 남아 있는 거래는 제거한다. 정상 0건 응답은 그 월의 기존 거래를 제거한다.
- 외부 거래 고유 ID가 없는 기본 API이므로 거래 값의 해시와 동일 값 등장 횟수로 식별한다. 동일 조건의 서로 다른 거래를 무조건 하나로 합치지 않는다. 값이 같은 재수집은 내부 ID를 유지한다. 정정·취소는 같은 월을 다시 수집했을 때 반영된다.
- 정정된 거래는 새 내부 ID가 생길 수 있다. 취소·삭제된 거래의 기존 관심 링크는 404가 될 수 있다. 가상/공공데이터의 브라우저 관심 저장 영역은 분리한다.
- 5초 연결 / 20초 요청 제한, 일시적 네트워크·429·5xx 오류는 최대 3회 시도한다. 인증 오류와 XML 업무 오류는 자동 재시도하지 않는다.
- 실패하면 기존 DB를 보존하고 앱 시작을 실패 처리한다. 키·승인·네트워크를 확인하거나 수집 옵션을 끄고 기존 데이터로 실행한다. 인증키·전체 요청 URL·원본 응답은 로그에 출력하지 않는다.
- XML의 외부 엔티티/DOCTYPE를 금지한다. 성공 코드, 지역, 월, 값 범위와 전체 페이지를 검증한다.

## 현재 범위와 한계

- Java 수집 코드, 합성 XML/모의 HTTP 기반 테스트 및 H2 저장 검증을 구현했다. **2026-10-26 실제 연결 및 종로구 202609 거래 32건의 저장·검색·상세 조회를 확인했다.** IntelliJ 환경 변수는 터미널에 자동 전달되지 않는다.
- 자동 스케줄링, 전국 지역 코드, Spring Batch, 수집 실패 이력 보관, 원본 응답 보관은 후속 과제다. DB에는 최근 성공 월·건수·시각을 저장한다.
- 페이지 수는 최대 100, 전체 결과는 최대 10,000건으로 제한한다. 수집 도중 동일 건수의 내용 변경까지 완벽한 시점 일관성을 보장하지는 않는다.
- 전용면적은 소수 4자리까지 보존한다. 면적·층·건축연도 등이 비어 있거나 지원 범위를 벗어나면 추정값을 넣지 않고 전체 수집을 중단한다. 실제 응답으로 누락값 정책을 보완할 수 있다.
- PostgreSQL용 구성은 유지하지만 이번 수집 테스트는 H2에서 수행했다. postgres에 사용할 때는 샘플이 없는 DB와 `DEMO_DATA=false`가 필요하다.
- `data/` 경로는 실행 작업 디렉터리 기준이다. 동시에 여러 프로세스가 같은 파일 DB를 열지 않는다.

## 공식 명세

[국토교통부_아파트 매매 실거래가 자료](https://www.data.go.kr/data/15126469/openapi.do)의 Swagger 명세(2026-10-26 확인)를 기준으로 구현했다.

엔드포인트: `https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade`

요청: serviceKey, LAWD_CD, DEAL_YMD 및 페이지 파라미터 pageNo/numOfRows. 응답: XML의 header/resultCode, body/items/item, totalCount, pageNo, numOfRows. 주요 항목은 sggCd, umdNm, aptNm, jibun, excluUseAr, dealYear/Month/Day, dealAmount, floor, buildYear, cdealType/cdealDay다.

## 검증 기록

- 구현 전 MolitTest 9개가 미구현 상태에서 실패/오류 발생(실패 5, 오류 4)을 확인했다.
- 소수 4자리 면적 테스트 실패를 추가 관측한 뒤 새 Flyway V3 마이그레이션과 파싱 정밀도를 반영했다.
- 최종 수집 테스트는 파싱, 오류/XXE 차단, 지역·월 검증, 페이지 수집, 키 인코딩·오류 메시지 비노출, 재시도, 중복/취소, 빈 응답, 트랜잭션 롤백, 검색 화면 연결을 포함한다.
- 테스트 XML은 검증용 합성 데이터이며 실제 공공데이터 수집 성공 증거가 아니다.

최종 `./mvnw -o -q verify`: 총 27개(기존 13 + 수집 14) 통과. 패키징된 JAR의 molit 프로필을 임시 파일 DB에서 기동해 빈 데이터 안내·검색 API·상태 확인까지 검증했고 테스트 서버를 종료했다.

## 인증키 인코딩 수정

포털의 Encoding 키에 있는 퍼센트 이스케이프를 한 번 복원한 뒤 요청에 한 번만 인코딩한다. Decoding 키의 `+`도 공백으로 바꾸지 않고 보존한다. 잘못된 퍼센트 인코딩은 외부 요청 전에 거부하며 키를 오류 메시지에 포함하지 않는다. IntelliJ에 설정된 키를 바꾸지 않아도 두 형식을 모두 사용할 수 있다.
