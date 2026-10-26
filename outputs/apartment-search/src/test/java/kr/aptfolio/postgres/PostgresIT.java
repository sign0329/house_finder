package kr.aptfolio.postgres;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kr.aptfolio.SearchService;
import kr.aptfolio.molit.MolitStore;
import kr.aptfolio.molit.MolitXml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL, never a production URL. Docker absence fails this opt-in suite. */
@Testcontainers
@ActiveProfiles("postgres")
@SpringBootTest(properties={"app.demo=false","molit.import-on-start=false"})
@AutoConfigureMockMvc
class PostgresIT {
 @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
  properties.add("spring.datasource.url",postgres::getJdbcUrl);
  properties.add("spring.datasource.username",postgres::getUsername);
  properties.add("spring.datasource.password",postgres::getPassword);
 }
 @Autowired JdbcTemplate db;
 @Autowired MolitStore store;
 @Autowired SearchService search;
 @Autowired MockMvc mvc;
 static final YearMonth MONTH=YearMonth.of(2026,9);
 @BeforeEach void resetOnlyContainerData() {
  db.update("delete from trade");
  db.update("update molit_import_state set last_month=null,last_count=null,updated_at=null where id=1");
 }
 private static MolitXml.Item item(String name,long price,LocalDate date,boolean cancelled) {
  return new MolitXml.Item(name,"서울 종로구 테스트동 1",new BigDecimal("84.9512"),7,2010,date,price,cancelled);
 }
 private static MolitXml.Item item(String name) {return item(name,82500,LocalDate.of(2026,9,15),false);}
 @Test void appliesAllMigrationsOnPostgres17() {
  assertTrue(db.queryForObject("select version()",String.class).startsWith("PostgreSQL 17."));
  assertEquals(3,db.queryForObject("select count(*) from flyway_schema_history where success=true and type='SQL'",Integer.class));
  assertEquals(4,db.queryForObject("select numeric_scale from information_schema.columns where table_schema='public' and table_name='trade' and column_name='area'",Integer.class));
  assertEquals(1,db.queryForObject("select count(*) from pg_indexes where indexname='idx_trade_source_key'",Integer.class));
 }
 @Test void storesParsedTradesAndServesSearchAndDetail() throws Exception {
  String xml="""
   <response><header><resultCode>000</resultCode></header><body><items><item>
   <sggCd>11110</sggCd><umdNm>테스트동</umdNm><jibun>1</jibun><aptNm>검증아파트</aptNm>
   <excluUseAr>84.9512</excluUseAr><floor>7</floor><buildYear>2010</buildYear>
   <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>15</dealDay><dealAmount>82,500</dealAmount>
   </item></items><totalCount>1</totalCount><numOfRows>100</numOfRows><pageNo>1</pageNo></body></response>
   """;
  store.replace(MONTH,new MolitXml().parse(xml,MONTH).items());
  var results=search.search(Map.of("q","검증","region","서울","minPrice","82500","maxPrice","82500"));
  assertEquals(1,results.total());assertEquals(new BigDecimal("84.9512"),results.items().getFirst().area());
  mvc.perform(get("/api/trades").param("q","검증")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
  mvc.perform(get("/trades/"+results.items().getFirst().id())).andExpect(status().isOk());
 }
 @Test void duplicateOccurrencesKeepIdsAndLiteralWildcardsAreSafe() {
  store.replace(MONTH,List.of(item("100%_주택"),item("100%_주택"),item("다른주택")));
  var ids=db.queryForList("select id from trade order by id",Long.class);
  store.replace(MONTH,List.of(item("다른주택"),item("100%_주택"),item("100%_주택")));
  assertEquals(ids,db.queryForList("select id from trade order by id",Long.class));
  assertEquals(2,search.search(Map.of("q","%_")).total());
  assertEquals(0,search.search(Map.of("q","' OR 1=1 --")).total());
 }
 @Test void correctionsCancellationsAndEmptySnapshotKeepOtherMonths() {
  store.replace(MONTH,List.of(item("정정대상"),item("해제대상")));
  store.replace(MONTH.plusMonths(1),List.of(item("다음달",90000,LocalDate.of(2026,10,1),false)));
  store.replace(MONTH,List.of(item("정정대상",83000,LocalDate.of(2026,9,15),false),item("해제대상",82500,LocalDate.of(2026,9,15),true)));
  assertEquals(2,search.search(Map.of()).total());
  assertEquals(83000,search.search(Map.of("q","정정대상")).items().getFirst().price());
  assertEquals(0,search.search(Map.of("q","해제대상")).total());
  store.replace(MONTH,List.of());
  assertEquals("다음달",search.search(Map.of()).items().getFirst().apartment());
 }
 @Test void databaseConstraintFailureRollsBackEntireReplacement() {
  store.replace(MONTH,List.of(item("기존")));
  var before=db.queryForList("select * from trade");
  var state=db.queryForList("select * from molit_import_state");
  assertThrows(DataIntegrityViolationException.class,()->store.replace(MONTH,List.of(item("새거래"),item("잘못된금액",-1,LocalDate.of(2026,9,15),false))));
  assertEquals(before,db.queryForList("select * from trade"));
  assertEquals(state,db.queryForList("select * from molit_import_state"));
 }
 @Test void concurrentImportsPublishOneCompleteSnapshot() throws Exception {
  var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)) {
   var first=pool.submit(()->{start.await();return store.replace(MONTH,List.of(item("A"),item("A")));});
   var second=pool.submit(()->{start.await();return store.replace(MONTH,List.of(item("B"),item("B"),item("B")));});
   start.countDown();assertEquals(2,first.get(15,TimeUnit.SECONDS));assertEquals(3,second.get(15,TimeUnit.SECONDS));
  }
  var names=db.queryForList("select distinct apartment from trade",String.class);
  assertEquals(1,names.size());
  int expected=names.getFirst().equals("A")?2:3;
  assertEquals(expected,db.queryForObject("select count(*) from trade",Integer.class));
  assertEquals(expected,db.queryForObject("select last_count from molit_import_state where id=1",Integer.class));
 }
 @Test void refusesDemoRowsWithoutDeletingThem() {
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price) values('서울','종로구','가상','주소',84,1,2010,'2026-09-01',100)");
  assertThrows(IllegalArgumentException.class,()->store.replace(MONTH,List.of(item("실제"))));
  assertEquals("DEMO",db.queryForObject("select source from trade",String.class));
 }
}
