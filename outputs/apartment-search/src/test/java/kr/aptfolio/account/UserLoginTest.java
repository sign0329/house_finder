package kr.aptfolio.account;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"collection.api.enabled=true","ADMIN_USERNAME=operator","spring.datasource.url=jdbc:h2:mem:user-login;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class UserLoginTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate db;
 @DynamicPropertySource static void credentials(DynamicPropertyRegistry r){r.add("ADMIN_PASSWORD_HASH",()->new BCryptPasswordEncoder(10).encode("admin-password-test"));}
 // Each test uses a separate connection address so rate limits remain enabled.
 private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(String name){return post("/signup").with(csrf()).with(r->{r.setRemoteAddr("192.0.2."+Math.abs(name.hashCode()%100));return r;}).param("username",name).param("nickname","테스트 사용자").param("password","user-password-test").param("confirmPassword","user-password-test");}
 @Test void signupStoresHashAndRejectsDuplicateUsername() throws Exception {
  mvc.perform(signup("newuser")).andExpect(redirectedUrl("/login?registered"));
  var hash=db.queryForObject("select password_hash from user_account where username='newuser'",String.class);
  assertNotEquals("user-password-test",hash);assertTrue(new BCryptPasswordEncoder().matches("user-password-test",hash));
  mvc.perform(signup("newuser")).andExpect(status().isConflict());
  assertEquals(1,db.queryForObject("select count(*) from user_account where username='newuser'",Integer.class));
 }
 @Test void signupRejectsInvalidInputAndDoesNotRenderPassword() throws Exception {
  mvc.perform(signup("bad")).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("user-password-test"))));
  mvc.perform(post("/signup").with(csrf()).param("username","validname").param("nickname","이름").param("password","short").param("confirmPassword","different")).andExpect(status().isBadRequest());
 }
 @Test void loginAndLogoutUseSessionWhileIncorrectPasswordFails() throws Exception {
  mvc.perform(signup("sessionuser")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/login").with(csrf()).param("username","sessionuser").param("password","wrong")).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
  var result=mvc.perform(post("/login").with(csrf()).param("username","sessionuser").param("password","user-password-test"))
   .andExpect(redirectedUrl("/account")).andExpect(authenticated().withRoles("USER")).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(get("/account").session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("sessionuser")));
  mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));assertTrue(session.isInvalid());
 }
 @Test void sameUsernameCannotCrossAdminAndUserAuthentication() throws Exception {
  mvc.perform(signup("operator")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/login").with(csrf()).param("username","operator").param("password","user-password-test")).andExpect(redirectedUrl("/admin/login?error"));
  mvc.perform(post("/login").with(csrf()).param("username","operator").param("password","admin-password-test")).andExpect(redirectedUrl("/login?error"));
  var result=mvc.perform(post("/login").with(csrf()).param("username","operator").param("password","user-password-test")).andExpect(authenticated().withRoles("USER")).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(get("/admin").session(session)).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/collection-jobs").session(session)).andExpect(status().isForbidden());
 }
 @Test void anonymousAccountIsProtectedAndAuthPostsRequireCsrf() throws Exception {
  mvc.perform(get("/account")).andExpect(redirectedUrl("/login"));
  mvc.perform(get("/")).andExpect(status().isOk());
  mvc.perform(get("/login")).andExpect(status().isOk());
  mvc.perform(get("/signup")).andExpect(status().isOk());
  mvc.perform(post("/signup")).andExpect(status().isForbidden());
  mvc.perform(post("/login")).andExpect(status().isForbidden());
  mvc.perform(post("/logout")).andExpect(status().isForbidden());
 }
}
