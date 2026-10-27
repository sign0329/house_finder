package kr.aptfolio.molit;

import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@SpringBootTest(properties={"app.demo=false","spring.datasource.url=jdbc:h2:mem:molit-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@Transactional
class MolitTest {
 static final YearMonth MONTH=YearMonth.of(2026,9);
 @Autowired org.springframework.test.web.servlet.MockMvc mvc;
 @Autowired MolitStore store;
 @Autowired JdbcTemplate db;
 static String item(String cancelled) {return """
 <item><sggCd>11110</sggCd><umdNm>테스트동</umdNm><jibun>1-2</jibun><aptNm>테스트 아파트</aptNm>
 <excluUseAr>84.95</excluUseAr><floor>7</floor><buildYear>2010</buildYear>
 <dealYear>2026</dealYear><dealMonth>9</dealMonth><dealDay>15</dealDay><dealAmount> 82,500 </dealAmount>
 <cdealType>%s</cdealType></item>
 """.formatted(cancelled);}
 static String page(int total,int number,int size,String items) {return "<response><header><resultCode>000</resultCode></header><body><items>"+items+"</items><totalCount>"+total+"</totalCount><pageNo>"+number+"</pageNo><numOfRows>"+size+"</numOfRows></body></response>";}
 @Test void importedProvinceAppearsInSearchOptions() throws Exception {
  var region=new MolitRegion("50110","제주","제주시");
  store.replace(region,MONTH,new MolitXml().parse(page(1,1,100,item("").replace("11110","50110")),region,MONTH).items());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.model().attribute("regions",org.hamcrest.Matchers.hasItem("제주")));
 }
 @Test void collectsTwoRegionsAndTwoMonthsAndReportsPartialFailure() throws Exception {
  var plan=MolitPlan.parse("11110:서울:종로구,11680:서울:강남구","202609","202608","202609");
  var reports=new java.util.ArrayList<MolitCollector.Result>();
  var client=new MolitClient("test",uri->{
   boolean gangnam=uri.getQuery().contains("LAWD_CD=11680");
   boolean august=uri.getQuery().contains("DEAL_YMD=202608");
   if(gangnam&&august)return new MolitClient.Reply(403,"secret upstream response");
   String xml=item("").replace("11110",gangnam?"11680":"11110").replace("<dealMonth>9",august?"<dealMonth>8":"<dealMonth>9");
   return new MolitClient.Reply(200,page(1,1,100,xml));
  });
  var results=new MolitCollector(client,store).collect(plan,reports::add);
  assertEquals(results,reports);
  assertEquals(List.of(true,true,false,true),results.stream().map(MolitCollector.Result::success).toList());
  assertEquals(3,db.queryForObject("select count(*) from trade",Integer.class));
  var ids=db.queryForList("select id from trade order by id",Long.class);
  new MolitCollector(client,store).collect(plan,r->{});
  assertEquals(ids,db.queryForList("select id from trade order by id",Long.class));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/trades").param("region","서울").param("q","강남구").param("from","2026-09-01").param("to","2026-09-30"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1));
 }
 @Test void regionRefreshAndEmptySnapshotPreserveOtherRegionAndMonth() {
  var gangnam=new MolitRegion("11680","서울","강남구");
  var items=new MolitXml().parse(page(1,1,100,item("").replace("11110","11680")),gangnam,MONTH).items();
  store.replace(gangnam,MONTH,items);
  store.replace(MONTH,new MolitXml().parse(page(1,1,100,item("")),MONTH).items());
  store.replace(MONTH,List.of());
  assertEquals(1,db.queryForObject("select count(*) from trade where source_region='11680'",Integer.class));
  var fail=new MolitClient("test",uri->new MolitClient.Reply(403,""));
  var result=new MolitCollector(fail,store).collect(MolitPlan.parse("11680:서울:강남구","202609","",""),r->{});
  assertFalse(result.getFirst().success());
  assertEquals(1,db.queryForObject("select count(*) from trade",Integer.class));
 }
 @Test void rejectsResponseFromDifferentRequestedRegion() {
  var gangnam=new MolitRegion("11680","서울","강남구");
  var client=new MolitClient("test",uri->new MolitClient.Reply(200,page(1,1,100,item(""))));
  assertThrows(IllegalArgumentException.class,()->client.fetch(gangnam,MONTH));
 }
 @Test void refreshingJongnoMustNotDeleteAnotherDistrictInSameMonth() {
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source,source_key,source_month,snapshot_id) values('서울','강남구','보존 대상','서울 강남구 테스트동',84,7,2010,'2026-09-15',82500,'MOLIT','other-region','202609','previous')");
  store.replace(MONTH,List.of());
  assertEquals(1,db.queryForObject("select count(*) from trade where district='강남구'",Integer.class));
 }
 @Test void collectedTradesAreSearchableWithSourceNotice() throws Exception {
  store.replace(MONTH,new MolitXml().parse(page(1,1,100,item("")),MONTH).items());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("출처: 국토교통부")));
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/trades").param("q","테스트 아파트"))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.total").value(1))
   .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.items[0].price").value(82500));
 }
 @Test
 @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void failedReplacementRollsBackCommittedData() {
  var item=new MolitXml().parse(page(1,1,100,item("")),MONTH).items().getFirst();
  try {
   store.replace(MONTH,List.of(item));
   long id=db.queryForObject("select id from trade",Long.class);
   var wrongMonth=new MolitXml.Item(item.apartment(),item.address(),item.area(),item.floor(),item.builtYear(),item.date().plusMonths(1),item.price(),false);
   assertThrows(IllegalArgumentException.class,()->store.replace(MONTH,List.of(item,item,wrongMonth)));
   assertEquals(1,db.queryForObject("select count(*) from trade",Integer.class));
   assertEquals(id,db.queryForObject("select id from trade",Long.class));
  } finally {store.replace(MONTH,List.of());}
 }
 @Test void keepsFourDecimalAreaInDatabase() {
  var item=new MolitXml().parse(page(1,1,100,item("").replace("84.95","84.9512")),MONTH).items();
  store.replace(MONTH,item);
  assertEquals(0,new java.math.BigDecimal("84.9512").compareTo(db.queryForObject("select area from trade",java.math.BigDecimal.class)));
 }
 @Test void emptySuccessClearsOnlyRequestedMonth() {
  var item=new MolitXml().parse(page(1,1,100,item("")),MONTH).items();
  store.replace(MONTH,item);
  assertEquals(0,store.replace(MONTH,new MolitXml().parse(page(0,1,100,""),MONTH).items()));
  assertEquals(0,db.queryForObject("select count(*) from trade",Integer.class));
 }
 @Test void transientHttpFailureRetriesButAuthenticationDoesNot() {
  AtomicInteger n=new AtomicInteger();
  var client=new MolitClient("test",uri->n.incrementAndGet()==1?new MolitClient.Reply(503,""):new MolitClient.Reply(200,page(0,1,100,"")));
  assertEquals(0,client.fetch(MONTH).size());assertEquals(2,n.get());
  n.set(0);
  assertThrows(IllegalArgumentException.class,()->new MolitClient("test",uri->{n.incrementAndGet();return new MolitClient.Reply(403,"");}).fetch(MONTH));
  assertEquals(1,n.get());
 }
 @Test void parsesMoneyAddressAndCancellation() {
  var p=new MolitXml().parse(page(2,1,100,item("")+item("O")),MONTH);
  assertEquals(82500,p.items().getFirst().price());
  assertEquals("서울 종로구 테스트동 1-2",p.items().getFirst().address());
  assertTrue(p.items().get(1).cancelled());
 }
 @Test void rejectsApiErrorsAndUnsafeXml() {
  assertThrows(IllegalArgumentException.class,()->new MolitXml().parse("<response><header><resultCode>30</resultCode></header></response>",MONTH));
  assertThrows(IllegalArgumentException.class,()->new MolitXml().parse("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><response>&e;</response>",MONTH));
 }
 @Test void rejectsWrongRegionMonthAndMissingValues() {
  for(String bad:List.of(item("").replace("11110","11680"),item("").replace("<dealMonth>9","<dealMonth>8"),item("").replace("84.95","")))
   assertThrows(IllegalArgumentException.class,()->new MolitXml().parse(page(1,1,100,bad),MONTH));
 }
 @Test void acceptsPortalEncodedKeysWithoutDoubleEncoding() {
  for(String key:List.of("a%2Bb%2Fc%3D","a%2bb%2fc%3d"," a%2Bb%2Fc%3D ")) {
   var client=new MolitClient(key,uri->{
    assertTrue(uri.getRawQuery().startsWith("serviceKey=a%2Bb%2Fc%3D&"));
    return new MolitClient.Reply(200,page(0,1,100,""));
   });
   assertTrue(client.fetch(MONTH).isEmpty());
  }
 }
 @Test void decodingEncodedKeyPreservesLiteralPlus() {
  var client=new MolitClient("a+b%2Fc%3D",uri->{
   assertTrue(uri.getRawQuery().startsWith("serviceKey=a%2Bb%2Fc%3D&"));
   return new MolitClient.Reply(200,page(0,1,100,""));
  });
  assertTrue(client.fetch(MONTH).isEmpty());
 }
 @Test void malformedKeyIsRejectedWithoutEchoOrNetworkCall() {
  var error=assertThrows(IllegalArgumentException.class,()->new MolitClient("PRIVATE%ZZ",uri->{
   fail("Malformed key must not reach the network");return null;
  }).fetch(MONTH));
  assertFalse(error.toString().contains("PRIVATE"));assertNull(error.getCause());
 }
 @Test void fetchesEveryPageAndEncodesRawKeyOnce() {
  AtomicInteger count=new AtomicInteger();
  var client=new MolitClient("a+b/c=",uri->{
   assertTrue(uri.toString().startsWith("https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/"));
   assertTrue(uri.getRawQuery().contains("serviceKey=a%2Bb%2Fc%3D"));
   int n=count.incrementAndGet();return new MolitClient.Reply(200,page(2,n,1,item("")));
  });
  assertEquals(2,client.fetch(MONTH).size());assertEquals(2,count.get());
 }
 @Test void incompleteSnapshotFailsInsteadOfReturningPartialData() {
  AtomicInteger count=new AtomicInteger();
  var client=new MolitClient("test",uri->new MolitClient.Reply(200,page(2,count.incrementAndGet(),1,count.get()==1?item(""):"")));
  assertThrows(IllegalArgumentException.class,()->client.fetch(MONTH));
 }
 @Test void httpFailureNeverExposesKeyOrTransportException() {
  var client=new MolitClient("TOP_SECRET",uri->{throw new java.io.IOException(uri.toString());});
  var error=assertThrows(IllegalArgumentException.class,()->client.fetch(MONTH));
  assertFalse(error.toString().contains("TOP_SECRET"));assertNull(error.getCause());
 }
 @Test void repeatedSnapshotPreservesIdsAndIdenticalLegitimateTransactions() {
  var items=new MolitXml().parse(page(2,1,100,item("")+item("")),MONTH).items();
  assertEquals(2,store.replace(MONTH,items));
  var ids=db.queryForList("select id from trade order by id",Long.class);
  assertEquals(2,store.replace(MONTH,items));
  assertEquals(ids,db.queryForList("select id from trade order by id",Long.class));
 }
 @Test void refreshRemovesCancelledAndCorrectsSnapshot() {
  store.replace(MONTH,new MolitXml().parse(page(1,1,100,item("")),MONTH).items());
  assertEquals(0,store.replace(MONTH,new MolitXml().parse(page(1,1,100,item("O")),MONTH).items()));
  assertEquals(0,db.queryForObject("select count(*) from trade",Integer.class));
 }
 @Test void refusesToMixDemoData() {
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price) values('서울','종로구','샘플','주소',84,1,2010,'2026-09-01',100)");
  assertThrows(IllegalArgumentException.class,()->store.replace(MONTH,List.of()));
  assertEquals(1,db.queryForObject("select count(*) from trade",Integer.class));
 }
}
