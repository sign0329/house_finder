package kr.aptfolio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="app.security.requests-per-minute=2") @AutoConfigureMockMvc
class SecurityTest {
 @Autowired MockMvc mvc; @Autowired Environment env;
 @Test void demoIsLoopbackOnly(){assertThat(env.getProperty("server.address")).isEqualTo("127.0.0.1");}
 @Test void securityHeaders() throws Exception {mvc.perform(get("/").with(r->{r.setRemoteAddr("192.0.2.1");return r;}))
  .andExpect(header().string("X-Content-Type-Options","nosniff"))
  .andExpect(header().string("X-Frame-Options","DENY"))
  .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("script-src 'self'")));}
 @Test void limitsCannotBeBypassedWithForwardedHeader() throws Exception {
  for(int i=0;i<2;i++) mvc.perform(get("/api/trades").with(r->{r.setRemoteAddr("192.0.2.2");return r;})).andExpect(status().isOk());
  mvc.perform(get("/api/trades").header("X-Forwarded-For","192.0.2.99").with(r->{r.setRemoteAddr("192.0.2.2");return r;}))
   .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
  mvc.perform(get("/actuator/health").with(r->{r.setRemoteAddr("192.0.2.2");return r;})).andExpect(status().isOk());
 }
}
