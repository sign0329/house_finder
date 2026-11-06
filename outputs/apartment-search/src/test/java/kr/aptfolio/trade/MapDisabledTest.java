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
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:map-disabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @Sql("/fixtures/search.sql")
class MapDisabledTest {
 @Autowired MockMvc mvc;
 @Test void missingKeyKeepsDetailAndExternalAddressSearchAvailable() throws Exception {
  mvc.perform(get("/trades/1")).andExpect(status().isOk())
   .andExpect(content().string(containsString("카카오맵에서 검색")))
   .andExpect(content().string(not(containsString("data-map-key="))))
   .andExpect(header().string("Content-Security-Policy",not(containsString("kakao.com"))));
 }
}
