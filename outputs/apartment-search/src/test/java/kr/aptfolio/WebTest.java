package kr.aptfolio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
