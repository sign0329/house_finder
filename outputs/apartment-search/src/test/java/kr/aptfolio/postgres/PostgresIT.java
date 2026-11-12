package kr.aptfolio.postgres;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kr.aptfolio.trade.SearchService;
import kr.aptfolio.collection.molit.MolitStore;
import kr.aptfolio.collection.molit.MolitXml;
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
@SpringBootTest(properties={"molit.import-on-start=false"})
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
 @Autowired kr.aptfolio.collection.jobs.CollectionJobs jobs;
 @Autowired SearchService search;
 @Autowired kr.aptfolio.trade.TradeTrends trends;
 @Autowired kr.aptfolio.bookmark.Bookmarks bookmarks;
 @Autowired kr.aptfolio.collection.jobs.CollectionCoverage coverage;
 @Autowired MockMvc mvc;
 static final YearMonth MONTH=YearMonth.of(2026,9);
 @BeforeEach void resetOnlyContainerData() {
  db.update("delete from collection_job");
  db.update("delete from trade");
  db.update("update molit_import_state set last_month=null,last_count=null,updated_at=null where id=1");
 }
 private static MolitXml.Item item(String name,long price,LocalDate date,boolean cancelled) {
  return new MolitXml.Item(name,"서울 종로구 테스트동 1",new BigDecimal("84.9512"),7,2010,date,price,cancelled);
 }
 private static MolitXml.Item item(String name) {return item(name,82500,LocalDate.of(2026,9,15),false);}
 @Test void concurrentBookmarksAreUniqueAndSurviveTradeRemoval() throws Exception {
  db.update("insert into user_account(username,nickname,password_hash) values('pg_bookmark','검증','fixture')");
  store.replace(MONTH,List.of(item("관심 단지")));long trade=db.queryForObject("select id from trade limit 1",Long.class);
  var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)) {
   java.util.concurrent.Callable<Long> save=()->{gate.await();return bookmarks.save("pg_bookmark",trade);};
   var a=pool.submit(save);var b=pool.submit(save);gate.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
  }
  assertEquals(1,bookmarks.list("pg_bookmark").size());db.update("delete from trade");
  assertNull(bookmarks.list("pg_bookmark").getFirst().tradeId());
  store.replace(MONTH,List.of(item("관심 단지")));assertNotNull(bookmarks.list("pg_bookmark").getFirst().tradeId());
 }
 @Test void concurrentUserSignupPersistsExactlyOneHashedAccount() throws Exception {
  var users=new kr.aptfolio.account.UserAccounts(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db));
  var gate=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)) {
   java.util.concurrent.Callable<Boolean> signup=()->{gate.await();try{users.register("pg_user","테스트 사용자","postgres-password-test","postgres-password-test");return true;}catch(org.springframework.dao.DuplicateKeyException e){return false;}};
   var a=pool.submit(signup);var b=pool.submit(signup);gate.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
  }
  assertEquals(1,db.queryForObject("select count(*) from user_account where username='pg_user'",Integer.class));
  var reloaded=new kr.aptfolio.account.UserAccounts(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(db)).loadUserByUsername("pg_user");
  assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("postgres-password-test",reloaded.getPassword()));
  assertEquals("ROLE_USER",reloaded.getAuthorities().iterator().next().getAuthority());
 }
 @Test void failureListOnPostgresExcludesSuccessAndDisablesStaleRetry() {
  var region=kr.aptfolio.collection.molit.MolitRegion.JONGNO;
  var failed=jobs.start(region,MONTH);jobs.fail(failed,"EXECUTOR_BUSY");
  assertTrue(jobs.failures(100).getFirst().retryable());
  jobs.complete(jobs.retry(failed),List.of());
  var rows=jobs.failures(100);assertEquals(1,rows.size());
  assertEquals(failed,rows.getFirst().job().id());assertFalse(rows.getFirst().retryable());
 }
 @Test void coverageAggregatesPostgresRowsAndKeepsLastSuccessfulTimestamp() {
  var region=kr.aptfolio.collection.molit.MolitRegion.JONGNO;
  jobs.complete(jobs.start(region,MONTH),List.of(item("거래")));
  var failed=jobs.start(region,MONTH);jobs.fail(failed,"FETCH_FAILED");
  var rows=coverage.list("11110","202608","202610");
  assertEquals(3,rows.size());assertEquals("NOT_COLLECTED",rows.get(0).status());
  assertEquals(1L,rows.get(1).tradeCount());assertEquals("FAILED",rows.get(1).status());
  assertNotNull(rows.get(1).lastSucceededAt());assertEquals(failed,rows.get(1).jobId());
  assertEquals("NOT_COLLECTED",rows.get(2).status());
 }
 @Test void upgradesExistingJongnoRowsWithoutChangingIdentity() {
  var config=org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("upgrade_test").defaultSchema("upgrade_test");
  config.target("3").load().migrate();
  db.update("insert into upgrade_test.trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source,source_key,source_month,snapshot_id) values('서울','종로구','기존 거래','서울 종로구 테스트동',84,7,2010,'2026-09-15',82500,'MOLIT','legacy-key','202609','legacy-snapshot')");
  long id=db.queryForObject("select id from upgrade_test.trade",Long.class);
  org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas("upgrade_test").defaultSchema("upgrade_test").load().migrate();
  assertEquals("11110",db.queryForObject("select source_region from upgrade_test.trade",String.class));
  assertEquals(id,db.queryForObject("select id from upgrade_test.trade",Long.class));
  assertEquals("legacy-key",db.queryForObject("select source_key from upgrade_test.trade",String.class));
 }
 @Test void regionSnapshotsAreIsolatedOnPostgres() {
  var region=new kr.aptfolio.collection.molit.MolitRegion("11680","서울","강남구");
  var jongno=new MolitXml.Item("동일 이름","서울 종로구 테스트동 1",new BigDecimal("84.95"),7,2010,LocalDate.of(2026,9,15),82500,false);
  var gangnam=new MolitXml.Item("동일 이름","서울 강남구 테스트동 1",new BigDecimal("84.95"),7,2010,LocalDate.of(2026,9,15),82500,false);
  store.replace(MONTH,List.of(jongno));store.replace(region,MONTH,List.of(gangnam));
  var ids=db.queryForList("select id from trade order by id",Long.class);
  store.replace(region,MONTH,List.of(gangnam));
  assertEquals(ids,db.queryForList("select id from trade order by id",Long.class));
  store.replace(MONTH,List.of());
  assertEquals(1,db.queryForObject("select count(*) from trade where source_region='11680'",Integer.class));
  assertEquals(1,search.search(Map.of("q","강남구","from","2026-09-01","to","2026-09-30")).total());
 }
 @Test void concurrentClaimsCreateOnlyOneRunningJob() throws Exception {
  var gate=new CountDownLatch(1);var region=kr.aptfolio.collection.molit.MolitRegion.JONGNO;
  try(var pool=Executors.newFixedThreadPool(2)){
   java.util.concurrent.Callable<Boolean> claim=()->{gate.await();try{jobs.start(region,MONTH);return true;}catch(IllegalStateException e){return false;}};
   var a=pool.submit(claim);var b=pool.submit(claim);gate.countDown();
   assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
  }
  assertEquals(1,db.queryForObject("select count(*) from collection_job where status='RUNNING'",Integer.class));
 }
 @Test void jobPublicationAndStaleWorkerFencingOnPostgres(){
  var id=jobs.start(kr.aptfolio.collection.molit.MolitRegion.JONGNO,MONTH);
  assertThrows(DataIntegrityViolationException.class,()->jobs.complete(id,List.of(item("valid"),item("bad",-1,MONTH.atDay(1),false))));
  assertEquals(0,db.queryForObject("select count(*) from trade",Integer.class));
  assertEquals("RUNNING",jobs.get(id).status());
  jobs.complete(id,List.of(item("valid")));assertEquals("SUCCEEDED",jobs.get(id).status());
  var expired=jobs.start(kr.aptfolio.collection.molit.MolitRegion.JONGNO,MONTH);
  db.update("update collection_job set lease_until=TIMESTAMP '2000-01-01 00:00:00' where id=?",expired);
  jobs.recover();var retry=jobs.retry(expired);
  assertThrows(IllegalStateException.class,()->jobs.complete(expired,List.of()));
  assertEquals(1,db.queryForObject("select count(*) from trade",Integer.class));
  jobs.complete(retry,List.of());assertEquals(2,jobs.get(expired).attempt());
 }
 @Test void sourceMustBeExplicitInsteadOfDefaultingToDemo() {
  assertThrows(DataIntegrityViolationException.class,()->db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price) values('서울','검증구','검증','주소',84,1,2010,'2026-09-01',100)"));
 }
 @Test void appliesAllMigrationsOnPostgres17() {
  assertTrue(db.queryForObject("select version()",String.class).startsWith("PostgreSQL 17."));
  assertEquals(9,db.queryForObject("select count(*) from flyway_schema_history where success=true and type='SQL'",Integer.class));
  assertEquals(4,db.queryForObject("select numeric_scale from information_schema.columns where table_schema='public' and table_name='trade' and column_name='area'",Integer.class));
  assertEquals(1,db.queryForObject("select count(*) from pg_indexes where schemaname='public' and indexname='idx_trade_source_key'",Integer.class));
  assertTrue(db.queryForObject("select indexdef from pg_indexes where schemaname='public' and indexname='idx_trade_recent_date'",String.class).endsWith("(deal_date DESC, id DESC)"));
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
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source) values('서울','종로구','가상','주소',84,1,2010,'2026-09-01',100,'TEST')");
  assertThrows(IllegalArgumentException.class,()->store.replace(MONTH,List.of(item("실제"))));
  assertEquals("TEST",db.queryForObject("select source from trade",String.class));
 }

 @Test void aggregatesMonthlyTrendOnPostgres(){
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source) values('서울','검증구','월별검증','주소',84.1234,1,2020,'2026-10-01',10000,'TEST'),('서울','검증구','월별검증','주소',84.1234,2,2020,'2026-10-31',20001,'TEST')");
  var trade=search.search(Map.of("q","월별검증")).items().getFirst();
  var trend=trends.monthly(trade,6);
  assertEquals(6,trend.points().size());assertEquals(2,trend.total());
  assertEquals(new BigDecimal("15000.50"),trend.points().getLast().averagePrice());
  assertNull(trend.points().getFirst().averagePrice());
 }
}
