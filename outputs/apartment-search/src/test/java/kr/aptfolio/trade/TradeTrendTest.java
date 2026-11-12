package kr.aptfolio.trade;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import java.time.YearMonth;
import java.math.BigDecimal;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:trendtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1") @AutoConfigureMockMvc @Transactional
class TradeTrendTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate db;
 @Autowired TradeTrends trends;
 @Autowired SearchService search;
 @Test void detailOffersMonthlyPriceAndVolume() throws Exception {
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source) values('서울','검증구','추이검증','검증주소',84,1,2020,'2026-10-01',10000,'TEST')");
  long id=db.queryForObject("select max(id) from trade where apartment='추이검증'",Long.class);
  mvc.perform(get("/trades/"+id)).andExpect(status().isOk()).andExpect(model().attributeExists("trend"));
 }

 private Trade add(String date,long price,String area,String address){
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source) values('서울','검증구','월별검증',?,?,1,2020,?,?,'TEST')",address,new BigDecimal(area),java.sql.Date.valueOf(date),price);
  long id=db.queryForObject("select max(id) from trade where apartment='월별검증'",Long.class);
  return search.find(id).orElseThrow();
 }
 @Test void aggregatesOnlySameApartmentAddressAndExactArea(){
  var t=add("2026-10-01",10000,"84.1234","주소A");
  add("2026-10-31",20001,"84.1234","주소A");
  add("2026-10-12",99999,"84.1235","주소A");
  add("2026-10-12",99999,"84.1234","주소B");
  var result=trends.monthly(t,6);var month=result.points().getLast();
  assertEquals(2,month.count());assertEquals(new BigDecimal("15000.50"),month.averagePrice());
  assertEquals(10000L,month.minPrice());assertEquals(20001L,month.maxPrice());assertEquals(2,result.total());
 }
 @Test void fillsGapsAndAnchorsToLatestSameAreaTrade(){
  var old=add("2025-01-01",9000,"84","주소A");
  add("2026-05-01",10000,"84","주소A");add("2026-10-31",12000,"84","주소A");
  var result=trends.monthly(old,6);
  assertEquals(YearMonth.of(2026,5),result.from());assertEquals(YearMonth.of(2026,10),result.to());
  assertEquals(6,result.points().size());assertEquals(2,result.total());
  assertNull(result.points().get(1).averagePrice());assertEquals(0,result.points().get(1).count());
  assertEquals(0,result.priceHeight(result.points().get(1)));assertEquals("거래 없음",result.points().get(1).averageLabel());
  assertEquals(36,trends.monthly(old,36).points().size());
 }
 @Test void validatesPeriodsAndMissingTrades() throws Exception {
  var t=add("2026-10-01",10000,"84","주소A");
  mvc.perform(get("/trades/"+t.id()).param("months","36")).andExpect(status().isOk());
  mvc.perform(get("/trades/"+t.id()).param("months","999")).andExpect(status().isBadRequest());
  mvc.perform(get("/trades/"+t.id()).param("months","bad")).andExpect(status().isBadRequest());
  mvc.perform(get("/trades/9223372036854775807")).andExpect(status().isNotFound());
 }
}
