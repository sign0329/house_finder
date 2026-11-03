package kr.aptfolio.collection.jobs;
import kr.aptfolio.collection.molit.MolitClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"collection.api.enabled=true","COLLECTION_ADMIN_TOKEN=01234567890123456789012345678901","MOLIT_SERVICE_KEY=fixture-only","spring.datasource.url=jdbc:h2:mem:job-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class CollectionJobApiTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db; @Autowired CollectionJobs jobs;
 @MockitoBean MolitClient client;
 static final String PATH="/api/admin/collection-jobs",TOKEN="01234567890123456789012345678901";
 @BeforeEach void reset(){db.update("delete from collection_job");db.update("delete from trade");}
 @Test void adminPageRendersOnlyShellWithoutCredentialsOrJobData() throws Exception {
  mvc.perform(get("/admin")).andExpect(status().isOk())
   .andExpect(view().name("admin"))
   .andExpect(header().string("Cache-Control","no-store"))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("수집 관리")))
   .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(TOKEN))));
  verifyNoInteractions(client);
 }
 @Test void rejectsMissingOrWrongTokenWithoutCallingUpstream() throws Exception {
  mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
  mvc.perform(post(PATH).header("X-Collection-Token","wrong").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());verifyNoInteractions(client);
 }
 @Test void validatesInputAndListsAndLooksUpJobs() throws Exception {
  mvc.perform(post(PATH).header("X-Collection-Token",TOKEN).contentType("application/json").content("{\"month\":\"bad\"}")).andExpect(status().isBadRequest());
  mvc.perform(get(PATH).header("X-Collection-Token",TOKEN)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get(PATH+"/missing").header("X-Collection-Token",TOKEN)).andExpect(status().isNotFound());verifyNoInteractions(client);
 }
 @Test void startsAsynchronouslyAndRetriesSanitizedFailure() throws Exception {
  when(client.fetch(any(),any())).thenThrow(new IllegalArgumentException("secret-upstream-url"));
  mvc.perform(post(PATH).header("X-Collection-Token",TOKEN).contentType("application/json").content("{\"regionCode\":\"11110\",\"province\":\"서울\",\"district\":\"종로구\",\"month\":\"202609\"}")).andExpect(status().isAccepted());
  awaitStatus("FAILED");var old=jobs.list(1).getFirst();assertEquals("FETCH_FAILED",old.failureType());
  doReturn(java.util.List.of()).when(client).fetch(any(),any());
  mvc.perform(post(PATH+"/"+old.id()+"/retry").header("X-Collection-Token",TOKEN)).andExpect(status().isAccepted());
  awaitStatus("SUCCEEDED");assertEquals(2,jobs.list(1).getFirst().attempt());assertEquals("FAILED",jobs.get(old.id()).status());
 }
 @Test void coverageDistinguishesMissingFromSuccessfulZero() throws Exception {
  var month=java.time.YearMonth.of(2026,9);
  jobs.complete(jobs.start(kr.aptfolio.collection.molit.MolitRegion.JONGNO,month),java.util.List.of());
  mvc.perform(get(PATH+"/coverage").header("X-Collection-Token",TOKEN).param("regionCode","11110").param("from","202608").param("to","202609"))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("NOT_COLLECTED"))
   .andExpect(jsonPath("$[1].status").value("SUCCEEDED")).andExpect(jsonPath("$[1].tradeCount").value(0))
   .andExpect(jsonPath("$[1].lastSucceededAt").isNotEmpty());
  verifyNoInteractions(client);
 }
 @Test void coverageRetainsLegacyRowsWithoutInventingSuccess() throws Exception {
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source,source_key,source_region,source_month) values('서울','종로구','기존 거래','주소',84,1,2010,'2026-09-01',100,'MOLIT','legacy','11110','202609')");
  mvc.perform(get(PATH+"/coverage").header("X-Collection-Token",TOKEN).param("regionCode","11110").param("from","202609").param("to","202609"))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("DATA_WITHOUT_HISTORY"))
   .andExpect(jsonPath("$[0].tradeCount").value(1)).andExpect(jsonPath("$[0].lastSucceededAt").isEmpty());
  mvc.perform(get(PATH+"/coverage").header("X-Collection-Token",TOKEN).param("regionCode","43111").param("from","202609").param("to","202609"))
   .andExpect(jsonPath("$[0].status").value("NOT_COLLECTED")).andExpect(jsonPath("$[0].tradeCount").value(0));
 }
 @Test void coverageShowsLatestFailureAndEarlierSuccess() throws Exception {
  var region=kr.aptfolio.collection.molit.MolitRegion.JONGNO;var month=java.time.YearMonth.of(2026,9);
  jobs.complete(jobs.start(region,month),java.util.List.of());var failed=jobs.start(region,month);jobs.fail(failed,"FETCH_FAILED");
  mvc.perform(get(PATH+"/coverage").header("X-Collection-Token",TOKEN).param("regionCode","11110").param("from","202609").param("to","202609"))
   .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("FAILED"))
   .andExpect(jsonPath("$[0].jobId").value(failed)).andExpect(jsonPath("$[0].lastSucceededAt").isNotEmpty());
 }
 @Test void coverageRequiresAuthenticationAndBoundedValidRange() throws Exception {
  mvc.perform(get(PATH+"/coverage").param("regionCode","11110").param("from","202609").param("to","202609")).andExpect(status().isUnauthorized());
  for(String[] range:new String[][]{{"202501","202609"},{"202609","202608"},{"202613","202613"}})
   mvc.perform(get(PATH+"/coverage").header("X-Collection-Token",TOKEN).param("regionCode","11110").param("from",range[0]).param("to",range[1])).andExpect(status().isBadRequest());
  mvc.perform(get(PATH+"/coverage").header("X-Collection-Token",TOKEN).param("regionCode","bad").param("from","202609").param("to","202609")).andExpect(status().isBadRequest());
  verifyNoInteractions(client);
 }
 private void awaitStatus(String expected) throws Exception {
  for(int i=0;i<100;i++){if(jobs.list(1).getFirst().status().equals(expected))return;Thread.sleep(20);}
  fail("worker did not reach "+expected);
 }
}
