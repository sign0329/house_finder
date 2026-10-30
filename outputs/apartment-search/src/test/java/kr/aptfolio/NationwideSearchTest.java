package kr.aptfolio;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.demo=false","spring.datasource.url=jdbc:h2:mem:nationwide;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc @Transactional
class NationwideSearchTest {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate db;
 @Autowired SearchService service;
 void insert(String region) {
  db.update("insert into trade(region,district,apartment,address,area,floor,built_year,deal_date,price,source) values(?,'테스트시','검증 아파트','테스트 주소',84,7,2010,'2026-09-15',82500,'MOLIT')",region);
 }
 @Test void allProvincesAvailableEvenWithEmptyDatabase() throws Exception {
  mvc.perform(get("/")).andExpect(status().isOk()).andExpect(model().attribute("regions",org.hamcrest.Matchers.hasSize(18)))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("전국")))
   .andExpect(content().string(org.hamcrest.Matchers.containsString("아직 수집되지 않은")));
 }
 @Test void nationwideQueryIncludesEveryStoredProvince() {
  for(String r:List.of("서울","인천","세종","강원특별자치도","전북특별자치도","제주"))insert(r);
  assertEquals(6,service.search(Map.of()).total());
 }
 @Test void shortAndOfficialProvinceNamesMatchSameTrades() {
  insert("전남광주통합특별시");insert("광주");insert("전남");
  assertEquals(1,service.search(Map.of("region","전남광주통합특별시")).total());
  assertEquals(1,service.search(Map.of("region","광주")).total());
  assertEquals(1,service.search(Map.of("region","전남")).total());
  insert("강원");insert("강원도");insert("강원특별자치도");insert("전북특별자치도");
  for(String name:List.of("강원","강원도","강원특별자치도"))assertEquals(3,service.search(Map.of("region",name)).total());
  assertEquals(1,service.search(Map.of("region","전북")).total());
 }
 @Test void provinceCombinesWithDistrictAndDateFilters() {
  insert("제주특별자치도");insert("서울");
  assertEquals(1,service.search(Map.of("region","제주","q","테스트시","from","2026-09-01","to","2026-09-30")).total());
  assertEquals(0,service.search(Map.of("region","제주","from","2026-10-01")).total());
 }
 @Test void unknownProvinceIsRejected() throws Exception {
  mvc.perform(get("/api/trades").param("region","없는지역")).andExpect(status().isBadRequest());
 }
}
