package kr.aptfolio.collection.jobs;
import kr.aptfolio.collection.molit.*;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:jobs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CollectionJobsTest {
 @Autowired CollectionJobs jobs;
 @Autowired JdbcTemplate db;
 @Autowired MolitStore store;
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 final MolitRegion region=MolitRegion.JONGNO;
 final YearMonth month=YearMonth.of(2026,9);
 @BeforeEach void reset(){db.update("delete from collection_job");db.update("delete from trade");}
 @Test void adminApiDisabledByDefault() throws Exception {
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/admin/collection-jobs")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
 }
 @Test void importerCollectorWritesHistoryWithoutPublicApi(){
  var client=new MolitClient("fixture",uri->new MolitClient.Reply(200,"<response><header><resultCode>000</resultCode></header><body><items/><totalCount>0</totalCount><pageNo>1</pageNo><numOfRows>100</numOfRows></body></response>"));
  var result=new MolitCollector(client,store,jobs).collect(MolitPlan.parse("11110:서울:종로구","202609","",""),r->{});
  assertTrue(result.getFirst().success());assertEquals("SUCCEEDED",jobs.list(1).getFirst().status());
 }
 @Test void duplicateRunningSegmentRejectedAndSuccessCannotRetry(){
  var id=jobs.start(region,month);
  assertThrows(IllegalStateException.class,()->jobs.start(region,month));
  jobs.complete(id,List.of());
  assertEquals("SUCCEEDED",jobs.get(id).status());
  assertThrows(IllegalStateException.class,()->jobs.retry(id));
 }
 @Test void failureRetriesAsNewAttemptWithoutLosingHistory(){
  var id=jobs.start(region,month);jobs.fail(id,"FETCH_FAILED");var retry=jobs.retry(id);
  assertNotEquals(id,retry);assertEquals(2,jobs.get(retry).attempt());assertEquals("FAILED",jobs.get(id).status());
 }
 @Test void expiredWorkerCannotCommitAndCanBeRecovered(){
  var id=jobs.start(region,month);db.update("update collection_job set lease_until=TIMESTAMP '2000-01-01 00:00:00' where id=?",id);
  jobs.recover();assertEquals("INTERRUPTED",jobs.get(id).status());
  var next=jobs.retry(id);assertThrows(IllegalStateException.class,()->jobs.complete(id,List.of()));
  assertEquals("RUNNING",jobs.get(next).status());
 }
 @Test void failedSnapshotRollsBackTradesAndSuccessState(){
  var id=jobs.start(region,month);
  var bad=new MolitXml.Item("bad","서울 종로구 주소",new java.math.BigDecimal("84"),1,2020,month.atDay(1),-1,false);
  assertThrows(RuntimeException.class,()->jobs.complete(id,List.of(bad)));
  assertEquals(0,db.queryForObject("select count(*) from trade",Integer.class));assertEquals("RUNNING",jobs.get(id).status());
  jobs.fail(id,"STORE_FAILED");assertEquals("FAILED",jobs.get(id).status());
 }
}
