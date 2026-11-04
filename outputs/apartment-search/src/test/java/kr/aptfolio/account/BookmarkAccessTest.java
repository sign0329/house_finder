package kr.aptfolio.account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:bookmark-access;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @Sql("/fixtures/search.sql")
class BookmarkAccessTest {
 @Autowired MockMvc mvc;
 @Test void anonymousSearchAndDetailOfferLoginInsteadOfSave() throws Exception {
  for(String path:new String[]{"/","/trades/1"})mvc.perform(get(path)).andExpect(status().isOk())
   .andExpect(content().string(containsString("로그인 후 관심 아파트 등록")))
   .andExpect(content().string(not(containsString("data-key="))));
  mvc.perform(get("/saved")).andExpect(redirectedUrl("/login"));
  mvc.perform(get("/api/account/session")).andExpect(status().isUnauthorized());
 }
 @Test void userCanSaveAndReadOwnStorageIdentity() throws Exception {
  mvc.perform(get("/").with(user("alice").roles("USER"))).andExpect(content().string(containsString("data-key=")))
   .andExpect(content().string(containsString("name=\"bookmark-user\" content=\"alice\"")));
  mvc.perform(get("/saved").with(user("alice").roles("USER"))).andExpect(status().isOk());
  mvc.perform(get("/api/account/session").with(user("alice").roles("USER"))).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
 }
 @Test void adminIsNotTreatedAsARegularUser() throws Exception {
  mvc.perform(get("/saved").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
  mvc.perform(get("/api/account/session").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
 }
}
