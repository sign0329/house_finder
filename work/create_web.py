from pathlib import Path
p=Path('outputs/apartment-search')
def put(name, text):
 f=p/name; f.parent.mkdir(parents=True,exist_ok=True); f.write_text(text.strip()+'\n')
put('pom.xml', '''
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
 <modelVersion>4.0.0</modelVersion>
 <parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.5.7</version><relativePath/></parent>
 <groupId>kr.aptfolio</groupId><artifactId>apartment-search</artifactId><version>0.1.0</version>
 <name>집결 — 아파트 실거래가</name>
 <properties><java.version>21</java.version></properties>
 <dependencies>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-thymeleaf</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
  <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
  <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
 </dependencies>
 <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
''')
# Acceptance tests are authored before production code. Execution waits on Maven availability.
put('src/test/java/kr/aptfolio/WebTest.java', '''
package kr.aptfolio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
@SpringBootTest @AutoConfigureMockMvc
class WebTest {
 @Autowired MockMvc mvc;
 @Test void rendersSearchAndDisclosesDemo() throws Exception {
  mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("가상 거래 데이터")));
 }
 @Test void filtersByRegionAndName() throws Exception {
  mvc.perform(get("/api/trades").param("region","서울").param("q","한강"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(greaterThan(0)))
   .andExpect(jsonPath("$.items[*].region",everyItem(is("서울"))))
   .andExpect(jsonPath("$.items[*].apartment",everyItem(containsString("한강"))));
 }
 @Test void impossibleSearchReturnsEmpty() throws Exception {
  mvc.perform(get("/api/trades").param("q","존재하지않는아파트"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
 }
 @Test void rejectsReversedPriceRange() throws Exception {
  mvc.perform(get("/api/trades").param("minPrice","90000").param("maxPrice","10000"))
   .andExpect(status().isBadRequest());
 }
 @Test void rejectsInvalidDatesAndPage() throws Exception {
  mvc.perform(get("/api/trades").param("from","wrong")).andExpect(status().isBadRequest());
  mvc.perform(get("/api/trades").param("page","-1")).andExpect(status().isBadRequest());
 }
 @Test void detailAndMissingTrade() throws Exception {
  mvc.perform(get("/trades/1")).andExpect(status().isOk()).andExpect(content().string(containsString("거래 상세")));
  mvc.perform(get("/trades/999999")).andExpect(status().isNotFound());
 }
 @Test void paginationAndInjectionAreSafe() throws Exception {
  mvc.perform(get("/api/trades").param("page","1")).andExpect(jsonPath("$.items",hasSize(12)));
  mvc.perform(get("/api/trades").param("q","' OR 1=1 --")).andExpect(jsonPath("$.total").value(0));
 }
 @Test void inclusivePriceBoundary() throws Exception {
  mvc.perform(get("/api/trades").param("minPrice","0").param("maxPrice","0"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
 }
}
''')
put('src/main/java/kr/aptfolio/ApartmentApplication.java', '''
package kr.aptfolio;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class ApartmentApplication {
 public static void main(String[] args) { SpringApplication.run(ApartmentApplication.class,args); }
}
''')
put('src/main/resources/application.yml', '''
spring:
  application:
    name: apartment-search
  profiles:
    default: demo
  thymeleaf:
    cache: false
server:
  port: ${PORT:8080}
  error:
    include-message: never
management:
  endpoints:
    web:
      exposure:
        include: health
app:
  demo: false
''')
put('src/main/resources/application-demo.yml', '''
spring:
  datasource:
    url: jdbc:h2:mem:apartments;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
    username: sa
    password:
app:
  demo: true
''')
put('src/main/resources/application-postgres.yml', '''
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/apartments}
    username: ${DB_USER:apartments}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
  thymeleaf:
    cache: true
app:
  demo: ${DEMO_DATA:false}
''')
put('src/main/resources/db/migration/V1__create_trade.sql', '''
CREATE TABLE trade (
 id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
 region VARCHAR(30) NOT NULL,
 district VARCHAR(50) NOT NULL,
 apartment VARCHAR(120) NOT NULL,
 address VARCHAR(200) NOT NULL,
 area NUMERIC(8,2) NOT NULL CHECK (area > 0),
 floor INTEGER NOT NULL,
 built_year INTEGER NOT NULL,
 deal_date DATE NOT NULL,
 price BIGINT NOT NULL CHECK (price > 0),
 source VARCHAR(20) NOT NULL DEFAULT 'DEMO'
);
CREATE INDEX idx_trade_region_date ON trade(region, deal_date DESC, id DESC);
CREATE INDEX idx_trade_apartment_date ON trade(apartment, deal_date DESC, id DESC);
''')
put('src/main/java/kr/aptfolio/Trade.java', '''
package kr.aptfolio;
import java.math.BigDecimal;
import java.time.LocalDate;
public record Trade(long id, String region, String district, String apartment, String address,
 BigDecimal area, int floor, int builtYear, LocalDate dealDate, long price) {
 public String priceLabel() {
  long eok=price/10000, rest=price%10000;
  return eok==0 ? String.format("%,d만",rest) : eok+"억"+(rest==0 ? "" : " "+String.format("%,d만",rest));
 }
 public String key() {return region+"|"+district+"|"+apartment+"|"+address;}
}
''')
put('src/main/java/kr/aptfolio/DemoData.java', '''
package kr.aptfolio;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Random;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @ConditionalOnProperty(name="app.demo",havingValue="true")
public class DemoData implements CommandLineRunner {
 private final JdbcTemplate db;
 public DemoData(JdbcTemplate db) {this.db=db;}
 @Override @Transactional public void run(String...args) {
  if(db.queryForObject("select count(*) from trade",Long.class)>0) return;
  String[][] homes={{"서울","마포구","한강 포레스트","마포구 샘플로 12"},
   {"서울","성동구","서울숲 리버뷰","성동구 샘플길 24"},
   {"서울","강동구","고덕 그린파크","강동구 샘플로 35"},
   {"경기","수원시","광교 레이크힐","수원시 샘플길 42"},
   {"경기","성남시","판교 센트럴","성남시 샘플로 8"},
   {"부산","해운대구","해운대 오션시티","해운대구 샘플길 17"},
   {"대전","유성구","유성 온빛마을","유성구 샘플로 21"},
   {"대구","수성구","수성 더테라스","수성구 샘플길 33"}};
  Random r=new Random(42);
  for(int i=0;i<240;i++) {
   String[] h=homes[i%homes.length];
   db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price) values(?,?,?,?,?,?,?,?,?)",
    h[0],h[1],h[2],h[3],new BigDecimal(new String[]{"59.98","84.95","114.72"}[i%3]),
    1+r.nextInt(28),2010+i%13,LocalDate.of(2026,10,12).minusDays(i*2L),
    35000L+(7-i%8)*10500L+r.nextInt(200)*100L);
  }
 }
}
''')
put('src/main/java/kr/aptfolio/SearchService.java', '''
package kr.aptfolio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
@Service
public class SearchService {
 private final NamedParameterJdbcTemplate db;
 private static final RowMapper<Trade> ROW=(r,n)->new Trade(r.getLong("id"),r.getString("region"),r.getString("district"),r.getString("apartment"),r.getString("address"),r.getBigDecimal("area"),r.getInt("floor"),r.getInt("built_year"),r.getDate("deal_date").toLocalDate(),r.getLong("price"));
 public SearchService(NamedParameterJdbcTemplate db) {this.db=db;}
 public record Results(List<Trade> items,long total,int page,int pages) {}
 public Results search(Map<String,String> input) {
  Map<String,Object> args=new HashMap<>(); StringBuilder where=new StringBuilder(" where 1=1");
  String q=input.getOrDefault("q","").trim();
  if(q.length()>100) throw new IllegalArgumentException("검색어는 100자 이내로 입력해주세요.");
  if(!q.isEmpty()) {where.append(" and (apartment like :q escape '!' or district like :q escape '!')");args.put("q","%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
  String region=input.getOrDefault("region","");
  if(!region.isEmpty()) {where.append(" and region=:region");args.put("region",region);}
  Long min=number(input,"minPrice"), max=number(input,"maxPrice");
  if(min!=null && max!=null && min>max) throw new IllegalArgumentException("최소 가격이 최대 가격보다 큽니다.");
  if(min!=null){where.append(" and price>=:min");args.put("min",min);}
  if(max!=null){where.append(" and price<=:max");args.put("max",max);}
  LocalDate from=date(input,"from"),to=date(input,"to");
  if(from!=null&&to!=null&&from.isAfter(to))throw new IllegalArgumentException("시작일이 종료일보다 늦습니다.");
  if(from!=null){where.append(" and deal_date>=:from");args.put("from",from);}
  if(to!=null){where.append(" and deal_date<=:to");args.put("to",to);}
  String area=input.getOrDefault("area","");
  switch(area){case "small"->where.append(" and area<60");case "medium"->where.append(" and area>=60 and area<=85");case "large"->where.append(" and area>85");case ""->{} default->throw new IllegalArgumentException("면적 조건을 확인해주세요.");}
  int page;
  try{page=Integer.parseInt(input.getOrDefault("page","0"));}catch(NumberFormatException e){throw new IllegalArgumentException("페이지 번호를 확인해주세요.");}
  if(page<0||page>10000)throw new IllegalArgumentException("페이지 범위를 벗어났습니다.");
  String order=switch(input.getOrDefault("sort","recent")){case "priceAsc"->"price asc, id desc";case "priceDesc"->"price desc, id desc";case "recent"->"deal_date desc, id desc";default->throw new IllegalArgumentException("정렬 조건을 확인해주세요.");};
  long total=db.queryForObject("select count(*) from trade"+where,args,Long.class);
  args.put("limit",12);args.put("offset",page*12);
  return new Results(db.query("select * from trade"+where+" order by "+order+" limit :limit offset :offset",args,ROW),total,page,(int)((total+11)/12));
 }
 private Long number(Map<String,String> in,String key){
  if(in.getOrDefault(key,"").isBlank())return null;
  try{long n=Long.parseLong(in.get(key));if(n<0)throw new NumberFormatException();return n;}
  catch(NumberFormatException e){throw new IllegalArgumentException("가격은 0 이상의 정수(만원)로 입력해주세요.");}
 }
 private LocalDate date(Map<String,String> in,String key){
  if(in.getOrDefault(key,"").isBlank())return null;
  try{return LocalDate.parse(in.get(key));}catch(Exception e){throw new IllegalArgumentException("날짜 형식을 확인해주세요.");}
 }
 public Optional<Trade> find(long id){return db.query("select * from trade where id=:id",Map.of("id",id),ROW).stream().findFirst();}
 public List<Trade> history(Trade t){return db.query("select * from trade where region=:r and district=:d and apartment=:a and address=:s and area=:area order by deal_date desc,id desc limit 12",Map.of("r",t.region(),"d",t.district(),"a",t.apartment(),"s",t.address(),"area",t.area()),ROW);}
}
''')
put('src/main/java/kr/aptfolio/WebController.java', '''
package kr.aptfolio;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
@Controller
public class WebController {
 private final SearchService service;
 @Value("${app.demo:false}") boolean demo;
 public WebController(SearchService service){this.service=service;}
 @ModelAttribute void shared(Model m){m.addAttribute("demo",demo);m.addAttribute("regions",List.of("서울","경기","부산","대전","대구"));}
 @GetMapping("/") String home(@RequestParam Map<String,String> params,Model m){
  var result=service.search(params);m.addAttribute("result",result);m.addAttribute("filters",params);
  m.addAttribute("previous",pageUrl(params,Math.max(0,result.page()-1)));
  m.addAttribute("next",pageUrl(params,result.page()+1));return "index";
 }
 private String pageUrl(Map<String,String> params,int page){var b=UriComponentsBuilder.fromPath("/");params.forEach((k,v)->{if(!k.equals("page"))b.queryParam(k,v);});return b.queryParam("page",page).build().encode().toUriString();}
 @GetMapping("/api/trades") @ResponseBody SearchService.Results api(@RequestParam Map<String,String> params){return service.search(params);}
 @GetMapping("/trades/{id}") String detail(@PathVariable long id,Model m){
  var t=service.find(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  var history=service.history(t);m.addAttribute("trade",t);m.addAttribute("history",history);
  m.addAttribute("chartMax",history.stream().mapToLong(Trade::price).max().orElse(1));return "detail";
 }
 @GetMapping("/saved") String saved(){return "saved";}
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 String invalid(IllegalArgumentException e,Model m){m.addAttribute("message",e.getMessage());return "error/400";}
}
''')
put('src/main/resources/templates/fragments.html', '''
<!DOCTYPE html><html lang="ko" xmlns:th="http://www.thymeleaf.org"><head th:fragment="head(title)">
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title th:text="${title} + ' · 집결'">집결</title>
<link rel="stylesheet" th:href="@{/css/app.css}" href="/css/app.css"><script defer th:src="@{/js/app.js}" src="/js/app.js"></script></head>
<body><header th:fragment="header(active)" class="header"><a class="brand" href="/"><span class="brand-icon">집</span>집결<span class="brand-sub">살 곳을 알아가는 기준</span></a><nav aria-label="주요 메뉴"><a href="/" th:classappend="${active=='search'} ? 'active'">실거래가 탐색</a><a href="/saved" th:classappend="${active=='saved'} ? 'active'">관심 아파트 <span id="saved-count" class="count">0</span></a></nav><span class="mode">PORTFOLIO DEMO</span></header>
<footer th:fragment="footer" class="footer"><span><b>집결</b> &nbsp; 집을 고르는 일, 데이터에서 시작하세요.</span><span th:if="${demo}">가상 거래 데이터 · 실제 시세가 아닙니다</span><span th:unless="${demo}">실거래 데이터 탐색</span></footer></body></html>
''')
put('src/main/resources/templates/index.html', '''
<!DOCTYPE html><html lang="ko" xmlns:th="http://www.thymeleaf.org"><head th:replace="~{fragments :: head('실거래가 탐색')}"></head><body>
<div th:replace="~{fragments :: header('search')}"></div><main class="shell">
<section class="hero"><div><div class="eyebrow"><span></span> APARTMENT INSIGHT</div><h1>내가 찾는 집,<br><em>거래의 흐름</em>부터.</h1><p>지역과 가격으로 좁혀보고, 실제 거래 기록으로 비교하세요.</p></div><div class="hero-art" aria-hidden="true"><div class="orbit"></div><div class="building b1"></div><div class="building b2"></div><div class="building b3"></div><span class="art-label">A BETTER PLACE TO CALL HOME</span></div></section>
<div th:if="${demo}" class="notice"><span class="notice-dot"></span><b>데모 둘러보기</b><span>가상 거래 데이터 240건으로 검색과 상세 화면을 체험할 수 있어요. 실제 시세가 아닙니다.</span></div>
<form method="get" action="/" class="search-panel"><div class="search-top"><label class="search-input"><span aria-hidden="true">⌕</span><input name="q" th:value="${filters.get('q')}" placeholder="아파트명 또는 시·군·구를 검색하세요" aria-label="아파트명 또는 지역명"></label><button class="primary" type="submit">실거래가 검색 <span>↗</span></button></div>
<div class="filters"><label>지역<select name="region"><option value="">전체 지역</option><option th:each="r:${regions}" th:value="${r}" th:text="${r}" th:selected="${r==filters.get('region')}"></option></select></label>
<label>전용면적<select name="area"><option value="">전체 면적</option><option value="small" th:selected="${filters.get('area')=='small'}">60㎡ 미만</option><option value="medium" th:selected="${filters.get('area')=='medium'}">60–85㎡</option><option value="large" th:selected="${filters.get('area')=='large'}">85㎡ 초과</option></select></label>
<label>최소 가격 · 만원<input type="number" min="0" name="minPrice" placeholder="제한 없음" th:value="${filters.get('minPrice')}"></label><label>최대 가격 · 만원<input type="number" min="0" name="maxPrice" placeholder="제한 없음" th:value="${filters.get('maxPrice')}"></label>
<label>계약 시작일<input type="date" name="from" th:value="${filters.get('from')}"></label><label>계약 종료일<input type="date" name="to" th:value="${filters.get('to')}"></label></div>
<div class="filter-bottom"><span>가격은 만원 단위로 입력해요. 예: 5억원 → 50000</span><a href="/">조건 초기화 ↺</a></div>
<div class="results-top"><h2>거래 내역 <span th:text="${#numbers.formatInteger(result.total,1,'COMMA')}">240</span></h2><select name="sort" aria-label="정렬" onchange="this.form.requestSubmit()"><option value="recent">최근 계약순</option><option value="priceAsc" th:selected="${filters.get('sort')=='priceAsc'}">낮은 가격순</option><option value="priceDesc" th:selected="${filters.get('sort')=='priceDesc'}">높은 가격순</option></select></div></form>
<div class="results-layout"><section aria-label="거래 검색 결과"><div class="table-wrap"><table><thead><tr><th>아파트 / 지역</th><th>거래금액</th><th>전용면적 / 층</th><th>계약일</th><th><span class="sr-only">관심 등록</span></th></tr></thead><tbody><tr th:each="t:${result.items}"><td><a class="apt-name" th:href="@{/trades/{id}(id=${t.id})}" th:text="${t.apartment}"></a><span class="sub" th:text="${t.region+' '+t.district}"></span></td><td><strong class="price" th:text="${t.priceLabel()}"></strong><span class="sub">매매</span></td><td><span th:text="${t.area+'㎡'}"></span><span class="sub" th:text="${t.floor+'층'}"></span></td><td class="date" th:text="${t.dealDate}"></td><td><button type="button" class="bookmark" th:attr="data-key=${t.key()},data-id=${t.id},data-name=${t.apartment},data-location=${t.region+' '+t.district}" aria-label="관심 아파트 등록" aria-pressed="false">☆</button></td></tr></tbody></table>
<div class="empty" th:if="${result.total==0}"><span>⌕</span><h3>조건에 맞는 거래가 없어요</h3><p>검색어를 바꾸거나 가격·기간 조건을 넓혀보세요.</p><a href="/" class="secondary">전체 거래 보기</a></div></div>
<nav class="pagination" aria-label="결과 페이지"><a th:if="${result.page>0}" th:href="${previous}">← 이전</a><span th:text="${result.page+1}+' / '+${result.pages==0?1:result.pages}"></span><a th:if="${result.page+1<result.pages}" th:href="${next}">다음 →</a></nav></section>
<aside><div class="guide-card"><span class="eyebrow">SEARCH GUIDE</span><h3>숫자 너머의<br>집을 살펴보세요.</h3><p>같은 아파트도 면적과 층, 계약 시점에 따라 거래금액이 달라져요.</p><div class="guide-line"></div><span class="guide-number">01</span><b>같은 면적으로 비교</b><p>전용면적을 맞추면 가격 흐름이 더 선명하게 보여요.</p><span class="guide-number">02</span><b>거래 날짜 확인</b><p>아파트명을 눌러 같은 면적의 최근 거래를 확인해보세요.</p></div><div class="small-note">관심 아파트는 현재 브라우저에 저장됩니다.</div></aside></div>
</main><div th:replace="~{fragments :: footer}"></div><div id="toast" role="status" aria-live="polite"></div></body></html>
''')
put('src/main/resources/templates/detail.html', '''
<!DOCTYPE html><html lang="ko" xmlns:th="http://www.thymeleaf.org"><head th:replace="~{fragments :: head('거래 상세')}"></head><body><div th:replace="~{fragments :: header('search')}"></div><main class="shell detail"><a class="back" href="/">← 실거래가 탐색</a><div class="detail-heading"><div><span class="eyebrow">거래 상세</span><h1 th:text="${trade.apartment}"></h1><p th:text="${trade.region+' '+trade.address}"></p></div><button class="bookmark big" th:attr="data-key=${trade.key()},data-id=${trade.id},data-name=${trade.apartment},data-location=${trade.region+' '+trade.district}" aria-label="관심 아파트 등록" aria-pressed="false">☆</button></div><div th:if="${demo}" class="notice">가상 거래 데이터 · 실제 아파트 및 시세와 무관한 데모입니다.</div>
<section class="metrics"><div><span>거래금액</span><strong th:text="${trade.priceLabel()}"></strong></div><div><span>전용면적</span><strong th:text="${trade.area+'㎡'}"></strong></div><div><span>거래 층 / 건축연도</span><strong th:text="${trade.floor+'층 / '+trade.builtYear+'년'}"></strong></div><div><span>계약일</span><strong th:text="${trade.dealDate}"></strong></div></section>
<section class="chart-card"><div class="section-heading"><h2>같은 면적의 거래 흐름</h2><span th:text="${trade.area+'㎡ · 최근 최대 12건'}"></span></div><div class="chart" role="img" aria-label="동일 면적 거래금액 막대 그래프. 아래 표에 상세 수치 제공."><div class="bar-column" th:each="t:${history}"><span class="bar-value" th:text="${t.priceLabel()}"></span><div class="bar" th:style="'height:'+${t.price*170/chartMax}+'px'"></div><span class="bar-date" th:text="${#temporals.format(t.dealDate,'yy.MM')}"></span></div></div><p class="small-note">왼쪽부터 최근 계약순 · 층과 개별 거래 조건은 다를 수 있어요.</p></section>
<div class="table-wrap"><table><thead><tr><th>계약일</th><th>거래금액</th><th>전용면적</th><th>층</th></tr></thead><tbody><tr th:each="t:${history}"><td th:text="${t.dealDate}"></td><td><strong th:text="${t.priceLabel()}"></strong></td><td th:text="${t.area+'㎡'}"></td><td th:text="${t.floor+'층'}"></td></tr></tbody></table></div></main><div th:replace="~{fragments :: footer}"></div><div id="toast" role="status" aria-live="polite"></div></body></html>
''')
put('src/main/resources/templates/saved.html', '''
<!DOCTYPE html><html lang="ko" xmlns:th="http://www.thymeleaf.org"><head th:replace="~{fragments :: head('관심 아파트')}"></head><body><div th:replace="~{fragments :: header('saved')}"></div><main class="shell detail"><span class="eyebrow">YOUR SHORTLIST</span><h1>관심 아파트</h1><p class="muted">다시 살펴보고 싶은 집을 모아두세요. 현재 브라우저에만 저장돼요.</p><div id="saved-list" class="saved-grid"></div><div id="saved-empty" class="empty"><h3>아직 저장한 아파트가 없어요</h3><p>검색 결과에서 별을 누르면 여기에 모아볼 수 있어요.</p><a href="/" class="primary">아파트 둘러보기 →</a></div></main><div th:replace="~{fragments :: footer}"></div><div id="toast" role="status" aria-live="polite"></div></body></html>
''')
for code,msg in [('400','입력 조건을 확인해주세요.'),('404','찾으시는 거래가 없어요.'),('500','잠시 후 다시 시도해주세요.')]:
 put('src/main/resources/templates/error/'+code+'.html','''<!DOCTYPE html><html lang="ko" xmlns:th="http://www.thymeleaf.org"><head th:replace="~{fragments :: head('안내')}"></head><body><main class="shell empty"><span class="eyebrow">'''+code+'''</span><h1>'''+msg+'''</h1><p th:if="${message}" th:text="${message}"></p><a href="/" class="primary">검색으로 돌아가기</a></main></body></html>''')
put('src/main/resources/static/js/app.js', '''
(() => {
 const STORE='aptfolio.saved.v1'; let entries=[];
 try {const raw=JSON.parse(localStorage.getItem(STORE)||'[]');if(Array.isArray(raw))entries=raw.filter(x=>x&&typeof x.key==='string'&&Number.isSafeInteger(Number(x.id))&&Number(x.id)>0&&typeof x.name==='string'&&typeof x.location==='string');}catch{}
 const toast=(text)=>{const el=document.getElementById('toast');if(!el)return;el.textContent=text;el.classList.add('show');setTimeout(()=>el.classList.remove('show'),2200);};
 const persist=()=>{try{localStorage.setItem(STORE,JSON.stringify(entries));return true;}catch{toast('브라우저 저장 공간을 사용할 수 없어요.');return false;}};
 const render=()=>{
  const count=document.getElementById('saved-count');if(count)count.textContent=entries.length;
  document.querySelectorAll('.bookmark').forEach(b=>{const on=entries.some(x=>x.key===b.dataset.key);b.textContent=on?'★':'☆';b.setAttribute('aria-pressed',String(on));b.setAttribute('aria-label',on?'관심 아파트 해제':'관심 아파트 등록');});
  const list=document.getElementById('saved-list');if(!list)return;list.replaceChildren();document.getElementById('saved-empty').hidden=entries.length>0;
  entries.forEach(item=>{const card=document.createElement('article');card.className='saved-card';const location=document.createElement('p');location.textContent=item.location;const a=document.createElement('a');a.href='/trades/'+Number(item.id);a.textContent=item.name;const remove=document.createElement('button');remove.className='secondary';remove.textContent='관심 해제';remove.onclick=()=>{const old=entries;entries=entries.filter(x=>x.key!==item.key);if(!persist())entries=old;render();};card.append(location,a,remove);list.append(card);});
 };
 document.querySelectorAll('.bookmark').forEach(b=>b.addEventListener('click',()=>{const old=entries;const exists=entries.some(x=>x.key===b.dataset.key);entries=exists?entries.filter(x=>x.key!==b.dataset.key):[...entries,{key:b.dataset.key,id:b.dataset.id,name:b.dataset.name,location:b.dataset.location}];if(!persist())entries=old;else toast(exists?'관심 아파트에서 해제했어요.':'관심 아파트에 저장했어요.');render();}));render();
})();
''')
put('src/main/resources/static/css/app.css', '''
@import url('');
:root{--ink:#1b302d;--muted:#76817b;--green:#1c6552;--line:#e5e9e3;--bg:#f7f8f4;--lime:#ddeaac}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;font-size:14px;line-height:1.6}a{color:inherit;text-decoration:none}button,input,select{font:inherit}button,a,select{touch-action:manipulation}button{cursor:pointer}a:focus-visible,button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #91b752;outline-offset:3px}.header{height:88px;padding:0 5%;display:flex;align-items:center;border-bottom:1px solid var(--line);background:#fff;gap:36px}.brand{font-size:25px;font-weight:800;display:flex;align-items:center;gap:10px;letter-spacing:-1px}.brand-icon{background:var(--green);color:var(--lime);border-radius:9px;font-size:17px;width:34px;height:34px;display:grid;place-items:center;transform:rotate(-6deg)}.brand-sub{font-size:11px;letter-spacing:0;font-weight:400;color:var(--muted);margin-left:8px}.header nav{display:flex;gap:28px;margin-left:auto;height:100%;align-items:center}.header nav a{height:100%;display:flex;align-items:center;gap:7px;color:var(--muted);font-weight:600}.header nav .active{color:var(--green);border-bottom:3px solid var(--green)}.count{font-size:10px;background:#eef2e9;padding:1px 6px;border-radius:5px}.mode{font-size:9px;letter-spacing:1px;border:1px solid var(--line);border-radius:5px;padding:6px 9px;color:var(--muted)}.shell{max-width:1230px;padding:0 32px;margin:auto;min-height:calc(100vh - 200px)}.hero{display:flex;justify-content:space-between;align-items:center;min-height:295px;padding:38px 0}.eyebrow{font-size:10px;letter-spacing:2px;color:var(--green);font-weight:700}.eyebrow>span{display:inline-block;width:7px;height:7px;border-radius:50%;background:var(--green);margin-right:8px}h1{font-size:43px;line-height:1.25;letter-spacing:-2px;margin:17px 0}h1 em{font-style:normal;color:var(--green)}.hero p,.detail-heading p,.muted{color:var(--muted)}.hero-art{width:350px;height:210px;position:relative;overflow:hidden}.orbit{position:absolute;width:220px;height:220px;border-radius:50%;background:#e8eedb;right:28px;top:7px}.building{position:absolute;bottom:25px;transform:skewY(-8deg);background-color:#b6c5ad;background-image:repeating-linear-gradient(0deg,transparent 0 20px,#f5f7ec 20px 23px),repeating-linear-gradient(90deg,transparent 0 17px,#eff3e8 17px 20px);border:9px solid #b6c5ad;box-shadow:15px 8px 0 #8da38e}.b1{width:75px;height:98px;left:45px}.b2{width:92px;height:158px;left:130px;background-color:#789b83;border-color:#789b83;box-shadow:18px 9px 0 #456e5e}.b3{width:66px;height:76px;left:234px}.art-label{position:absolute;bottom:0;right:26px;font-size:8px;letter-spacing:2px;color:#8d9a83}.notice{padding:13px 17px;border:1px solid #e0e6d2;background:#f0f4e8;border-radius:8px;display:flex;align-items:center;gap:11px;font-size:12px;color:#5c6d54;margin-bottom:22px}.notice-dot{width:6px;height:6px;background:#78924d;border-radius:50%;flex-shrink:0}.search-panel{margin:0}.search-top{display:flex;gap:12px;padding:22px 22px 18px;background:white;border:1px solid var(--line);border-bottom:0;border-radius:12px 12px 0 0}.search-input{display:flex;align-items:center;flex:1;gap:10px;background:var(--bg);border:1px solid var(--line);border-radius:7px;padding:0 15px}.search-input span{font-size:27px;color:var(--green)}.search-input input{background:transparent;border:0;width:100%;padding:13px 0;outline:none}.primary{background:var(--green);color:white;border:0;border-radius:7px;padding:13px 21px;font-weight:600;display:inline-flex;align-items:center;gap:26px}.primary:hover{background:#174e40}.filters{display:grid;grid-template-columns:repeat(6,1fr);gap:12px;padding:0 22px 17px;background:#fff;border-left:1px solid var(--line);border-right:1px solid var(--line)}.filters label{font-size:11px;font-weight:600;color:#6b786e}.filters input,.filters select{display:block;width:100%;height:40px;border:1px solid var(--line);border-radius:6px;background:white;color:var(--ink);padding:8px;margin-top:7px;min-width:0}.filter-bottom{display:flex;justify-content:space-between;gap:10px;padding:0 22px 18px;background:white;border:1px solid var(--line);border-top:0;border-radius:0 0 12px 12px;font-size:10px;color:var(--muted)}.filter-bottom a{color:var(--green)}.results-top{display:flex;justify-content:space-between;align-items:center;margin:30px 0 15px}.results-top h2{font-size:18px;margin:0;letter-spacing:-.5px}.results-top h2 span{color:var(--green);margin-left:8px}.results-top select{border:1px solid var(--line);border-radius:6px;background:transparent;padding:6px 10px;font-size:12px;color:#5c6962}.results-layout{display:grid;grid-template-columns:minmax(0,1fr) 240px;gap:24px}.table-wrap{border:1px solid var(--line);border-radius:10px;overflow:auto;background:white}table{border-collapse:collapse;width:100%;white-space:nowrap;text-align:left}th{font-size:11px;font-weight:500;background:#f1f3ee;color:#7a857a;padding:12px 18px}td{padding:17px 18px;border-top:1px solid #edf0e9;font-size:13px}tr:hover td{background:#fcfdf9}.apt-name{font-weight:650;font-size:14px}.apt-name:hover{text-decoration:underline;color:var(--green)}.sub{display:block;font-size:10px;color:#8a928a;margin-top:3px}.price{font-size:16px;letter-spacing:-.5px}.date{color:#7f897e;font-size:11px}.bookmark{border:1px solid var(--line);border-radius:7px;background:white;color:#929d8e;width:32px;height:32px;font-size:22px;line-height:1}.bookmark[aria-pressed=true]{color:var(--green);background:#edf3df}.guide-card{background:#eaf0e3;border:1px solid #e1e8d8;border-radius:10px;padding:24px}.guide-card .eyebrow{font-size:9px}.guide-card h3{font-size:23px;line-height:1.4;letter-spacing:-1px;margin:14px 0}.guide-card p{font-size:12px;color:#74806b;line-height:1.9}.guide-line{height:1px;background:#d8e1cd;margin:22px 0}.guide-number{font-size:10px;display:inline-block;color:#6b8560;margin-right:8px}.guide-card b{font-size:12px}.small-note{font-size:11px;color:#8a9584;margin:13px 5px}.pagination{display:flex;justify-content:center;align-items:center;gap:24px;padding:25px;color:#718067;font-size:12px}.pagination a{padding:7px 13px;background:#fff;border:1px solid var(--line);border-radius:6px}.footer{max-width:1166px;margin:35px auto 0;border-top:1px solid var(--line);padding:24px 0;display:flex;justify-content:space-between;font-size:10px;color:#8b9587}.footer b{color:var(--green);font-size:15px}.empty{text-align:center;padding:65px 20px}.empty>span{font-size:30px}.empty p{color:var(--muted);margin-bottom:25px}.secondary{display:inline-block;background:white;border:1px solid var(--line);border-radius:6px;padding:8px 13px;color:var(--green);font-size:12px}.detail{padding-top:35px}.back{font-size:12px;color:var(--muted)}.detail-heading{display:flex;align-items:center;justify-content:space-between;margin:30px 0}.detail-heading h1{font-size:36px}.big{width:46px;height:46px;font-size:30px}.metrics{display:grid;grid-template-columns:repeat(4,1fr);background:white;border:1px solid var(--line);border-radius:10px;padding:28px 15px;margin:25px 0}.metrics>div{padding:0 20px;border-right:1px solid var(--line)}.metrics>div:last-child{border:0}.metrics span{font-size:11px;color:var(--muted);display:block}.metrics strong{display:block;font-size:23px;margin-top:7px;letter-spacing:-1px}.chart-card{background:white;padding:25px;border:1px solid var(--line);border-radius:10px;margin-bottom:24px}.section-heading{display:flex;align-items:center;justify-content:space-between}.section-heading h2{font-size:17px}.section-heading>span{font-size:11px;color:var(--muted)}.chart{display:flex;align-items:flex-end;gap:14px;min-height:230px;padding-top:20px;overflow:auto}.bar-column{flex:1;min-width:65px;text-align:center}.bar{max-width:42px;margin:8px auto;background:#bfd4ac;border-radius:5px 5px 0 0}.bar-column:first-child .bar{background:var(--green)}.bar-value,.bar-date{font-size:10px;color:#7c8875}.saved-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin-top:35px}.saved-card{background:white;border:1px solid var(--line);border-radius:10px;padding:24px}.saved-card p{font-size:12px;color:var(--muted)}.saved-card a{display:block;font-size:21px;font-weight:650;margin-bottom:25px}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0)}#toast{position:fixed;bottom:30px;left:50%;transform:translateX(-50%);background:var(--ink);color:#fff;padding:13px 24px;border-radius:9px;opacity:0;pointer-events:none;transition:opacity .2s;font-size:13px}#toast.show{opacity:1}[hidden]{display:none!important}@media(max-width:1000px){.brand-sub,.mode{display:none}.filters{grid-template-columns:repeat(3,1fr)}.results-layout{grid-template-columns:1fr}.results-layout aside{display:none}.footer{margin:25px 32px 0}.metrics strong{font-size:18px}}@media(max-width:650px){.header{height:72px;padding:0 20px;gap:10px}.header nav{gap:15px;font-size:12px}.brand{font-size:21px}.shell{padding:0 18px}.hero{min-height:240px;padding:30px 0}.hero h1{font-size:34px}.hero p{font-size:12px;max-width:230px}.hero-art{width:120px;height:190px;opacity:.7}.b1{left:15px;width:45px}.b2{left:63px;width:48px;height:140px}.b3,.art-label{display:none}.orbit{width:140px;height:140px;right:-45px;top:40px}.notice{font-size:10px;align-items:flex-start;flex-wrap:wrap;gap:6px}.search-top{padding:14px;gap:8px}.primary{padding:12px;font-size:12px;gap:8px}.search-input{padding:0 8px}.search-input input{font-size:11px}.filters{grid-template-columns:repeat(2,1fr);padding:0 14px 14px}.filter-bottom{padding:0 14px 14px;font-size:9px}th,td{padding:13px 12px}.metrics{grid-template-columns:repeat(2,1fr);gap:20px}.metrics>div:nth-child(2){border:0}.detail{padding-top:25px}.detail-heading h1{font-size:29px}.saved-grid{grid-template-columns:1fr}.footer{margin:25px 18px 0;display:block}.footer>span{display:block;margin-bottom:7px}.chart-card{padding:16px}.section-heading{display:block}}
'''.replace("@import url('');\n",''))
