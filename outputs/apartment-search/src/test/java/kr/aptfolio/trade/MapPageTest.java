package kr.aptfolio.trade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
@SpringBootTest(properties={"KAKAO_MAP_JAVASCRIPT_KEY=0123456789abcdef0123456789abcdef","spring.datasource.url=jdbc:h2:mem:map-enabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc @Sql("/fixtures/search.sql")
class MapPageTest {
 @Autowired MockMvc mvc;
 @Test void enabledMapIsLazyAndCspOnlyAllowsSdkOnDetail() throws Exception {
  mvc.perform(get("/trades/1")).andExpect(status().isOk()).andExpect(content().string(containsString("지도 보기")))
   .andExpect(content().string(containsString("data-map-key=\"0123456789abcdef0123456789abcdef\"")))
   .andExpect(content().string(not(containsString("src=\"https://dapi.kakao.com"))))
   .andExpect(header().string("Content-Security-Policy",containsString("https://dapi.kakao.com")));
  mvc.perform(get("/")).andExpect(header().string("Content-Security-Policy",not(containsString("kakao.com"))));
 }
}
