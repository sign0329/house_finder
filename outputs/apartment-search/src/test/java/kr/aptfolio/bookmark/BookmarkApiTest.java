package kr.aptfolio.bookmark;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:bookmark-db;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @Sql("/fixtures/search.sql")
class BookmarkApiTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db;
 static final String API="/api/account/bookmarks";
 @BeforeEach void accounts(){db.update("delete from user_account");db.update("insert into user_account(username,nickname,password_hash) values('alice','앨리스','fixture'),('bob','밥','fixture')");}
 private void save(long id) throws Exception {mvc.perform(post(API).with(user("alice").roles("USER")).with(csrf()).contentType("application/json").content("{\"tradeId\":"+id+"}")).andExpect(status().isOk());}
 @Test void sameApartmentAcrossTradesIsSavedOnceAndVisibleOnlyToOwner() throws Exception {
  save(1);save(2);
  mvc.perform(get(API).with(user("alice").roles("USER"))).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("한강 검증아파트"));
  mvc.perform(get(API).with(user("bob").roles("USER"))).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void otherUserCannotDeleteBookmark() throws Exception {
  save(1);long id=db.queryForObject("select id from user_bookmark",Long.class);
  mvc.perform(delete(API+"/"+id).with(user("bob").roles("USER")).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(delete(API+"/"+id).with(user("alice").roles("USER")).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get(API).with(user("alice").roles("USER"))).andExpect(jsonPath("$.items.length()").value(0));
 }
 @Test void missingTradeDoesNotEraseSavedApartment() throws Exception {
  save(1);db.update("delete from trade");
  mvc.perform(get(API).with(user("alice").roles("USER"))).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].tradeId").isEmpty());
 }
 @Test void authenticationCsrfAndInvalidTradeAreRejected() throws Exception {
  mvc.perform(get(API)).andExpect(status().isUnauthorized());
  mvc.perform(get(API).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
  mvc.perform(post(API).with(user("alice").roles("USER")).contentType("application/json").content("{\"tradeId\":1}")).andExpect(status().isForbidden());
  mvc.perform(post(API).with(user("alice").roles("USER")).with(csrf()).contentType("application/json").content("{\"tradeId\":999999}")).andExpect(status().isNotFound());
 }
}
