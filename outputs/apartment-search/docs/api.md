# 검색 API

`GET /api/trades`는 HTML 검색 화면과 동일한 조회 로직을 사용한다.

| 파라미터 | 형식 / 의미 |
| --- | --- |
| q | 단지명 또는 시·군·구 부분 일치, 최대 100자 |
| region | 지역 문자열 일치 |
| minPrice, maxPrice | 0 이상의 정수, 만원, 경계 포함 |
| from, to | YYYY-MM-DD, 경계 포함 |
| area | small: 60㎡ 미만 / medium: 60~85㎡ / large: 85㎡ 초과 |
| sort | recent(기본), priceAsc, priceDesc |
| page | 0~10000, 기본 0, 페이지당 12건 |

```sh
curl --get 'http://localhost:8080/api/trades'   --data-urlencode 'region=서울'   --data-urlencode 'q=한강'   --data 'sort=recent&page=0'
```

응답 필드: `items`(거래 배열), `total`(전체 건수), `page`(현재 페이지), `pages`(총 페이지). 거래에는 id, region, district, apartment, address, area, floor, builtYear, dealDate, price가 포함된다. price 단위는 만원이다.

잘못된 검색 조건은 400, 존재하지 않는 상세 거래는 404, 요청 제한 초과는 429다. 검색 API의 잘못된 조건도 현재 공통 HTML 오류 화면을 반환한다. JSON 오류 스키마 통일은 후속 과제다. 빈 결과와 범위 내 결과 없는 페이지는 200 및 빈 items를 반환한다.
