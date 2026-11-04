package kr.aptfolio.collection.jobs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"collection.api.enabled=true","ADMIN_USERNAME=operator","COLLECTION_ADMIN_TOKEN=test-legacy-token-0123456789012345","spring.datasource.url=jdbc:h2:mem:admin-login;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@AutoConfigureMockMvc
class AdminLoginTest {
 @Autowired MockMvc mvc;
 @DynamicPropertySource static void credentials(DynamicPropertyRegistry registry) {
  registry.add("ADMIN_PASSWORD_HASH",()->new BCryptPasswordEncoder(10).encode("test-admin-password"));
 }
 @Test void anonymousPageRedirectsAndApiRejectsWhileSearchRemainsPublic() throws Exception {
  mvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
  mvc.perform(get("/admin/login")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("관리자 로그인")));
  mvc.perform(get("/api/admin/collection-jobs")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/trades")).andExpect(status().isOk());
 }
 @Test void wrongPasswordFailsWithoutAuthentication() throws Exception {
  mvc.perform(post("/admin/login").with(csrf()).param("username","operator").param("password","wrong"))
   .andExpect(redirectedUrl("/admin/login?error")).andExpect(unauthenticated());
 }
 @Test void loginRotatesSessionAndLogoutInvalidatesAccess() throws Exception {
  var before=new MockHttpSession();String old=before.getId();
  var result=mvc.perform(post("/admin/login").session(before).with(r->{r.setRequestedSessionId(old);r.setRequestedSessionIdValid(true);return r;}).with(csrf()).param("username","operator").param("password","test-admin-password"))
   .andExpect(redirectedUrl("/admin")).andExpect(authenticated().withUsername("operator")).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);assertNotNull(session);assertNotEquals(old,session.getId());
  mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/collection-jobs").session(session)).andExpect(status().isOk());
  mvc.perform(post("/admin/logout").session(session).with(csrf())).andExpect(redirectedUrl("/admin/login?logout"));
  assertTrue(session.isInvalid());mvc.perform(get("/api/admin/collection-jobs")).andExpect(status().isUnauthorized());
 }
 @Test void sessionMutationsRequireCsrfAndEmptyTokenCannotBypassIt() throws Exception {
  var result=mvc.perform(post("/admin/login").with(csrf()).param("username","operator").param("password","test-admin-password")).andReturn();
  var session=(MockHttpSession)result.getRequest().getSession(false);assertNotNull(session);
  mvc.perform(post("/api/admin/collection-jobs").session(session).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/collection-jobs").session(session).header("X-Collection-Token","wrong").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/admin/collection-jobs").session(session).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isServiceUnavailable());
  mvc.perform(post("/admin/logout").session(session)).andExpect(status().isForbidden());
 }
 @Test void loginAttemptsAreLimitedPerConnectionAddress() throws Exception {
  for(int i=0;i<10;i++)mvc.perform(post("/admin/login").with(r->{r.setRemoteAddr("192.0.2.25");return r;}).with(csrf()).param("username","operator").param("password","wrong"))
   .andExpect(redirectedUrl("/admin/login?error"));
  mvc.perform(post("/admin/login").with(r->{r.setRemoteAddr("192.0.2.25");return r;}).with(csrf()).param("username","operator").param("password","wrong"))
   .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
 }
}
