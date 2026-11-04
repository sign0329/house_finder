package kr.aptfolio.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

@Configuration
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class AdminSecurityConfiguration {
 @Bean PasswordEncoder adminPasswordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean UserDetailsService adminUsers(@Value("${collection.api.enabled:false}") boolean enabled,
   @Value("${ADMIN_USERNAME:}") String username,@Value("${ADMIN_PASSWORD_HASH:}") String hash) {
  if(!enabled)return new InMemoryUserDetailsManager();
  if(username.isBlank()||username.length()>64||!hash.matches("\\$2[aby]\\$(10|11|12|13|14)\\$[./A-Za-z0-9]{53}"))
   throw new IllegalArgumentException("관리 기능에는 ADMIN_USERNAME과 BCrypt(비용 10~14) ADMIN_PASSWORD_HASH 설정이 필요합니다.");
  return new InMemoryUserDetailsManager(User.withUsername(username).password(hash).roles("ADMIN").build());
 }
 // Optional script token path never loads browser sessions. The controller verifies the token.
 @Bean @Order(1) @ConditionalOnProperty(name="collection.api.enabled",havingValue="true")
 SecurityFilterChain tokenApi(HttpSecurity http) throws Exception {
  return http.securityMatcher(r -> r.getRequestURI().startsWith("/api/admin/") && r.getHeader("X-Collection-Token") != null)
   .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .csrf(c -> c.disable()).requestCache(c -> c.disable())
   .authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
 }
 @Bean @Order(2) @ConditionalOnProperty(name="collection.api.enabled",havingValue="true")
 SecurityFilterChain adminSession(HttpSecurity http) throws Exception {
  return http.securityMatcher("/admin", "/admin/**", "/api/admin/**")
   .authorizeHttpRequests(a -> a.requestMatchers("/admin/login").permitAll().anyRequest().hasRole("ADMIN"))
   .formLogin(f -> f.loginPage("/admin/login").loginProcessingUrl("/admin/login")
    .defaultSuccessUrl("/admin",true).failureUrl("/admin/login?error").permitAll())
   .logout(l -> l.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
   .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
   .requestCache(c -> c.disable())
   .exceptionHandling(e -> e.authenticationEntryPoint((request,response,exception) -> {
     if(request.getRequestURI().startsWith("/api/"))response.sendError(401);
     else new LoginUrlAuthenticationEntryPoint("/admin/login").commence(request,response,exception);
   })).build();
 }
 @Bean @Order(3) SecurityFilterChain publicPages(HttpSecurity http) throws Exception {
  return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable()).build();
 }
}
