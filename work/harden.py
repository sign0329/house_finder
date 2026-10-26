from pathlib import Path
p=Path('.')
f=p/'pom.xml';s=f.read_text().replace('<version>3.5.7</version>','<version>4.0.9</version>').replace('spring-boot-starter-web</','spring-boot-starter-webmvc</')
s=s.replace('</dependencies>','''<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
</dependencies>''')
s=s.replace('</plugins>','''<plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><configuration><argLine>-javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine></configuration></plugin></plugins>''');f.write_text(s)
for f in (p/'src/test').rglob('*.java'):f.write_text(f.read_text().replace('org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc','org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc'))
f=p/'src/main/resources/application-demo.yml';f.write_text(f.read_text()+'\nserver:\n  address: 127.0.0.1\n')
f=p/'src/main/resources/application.yml';s=f.read_text().replace('  application:','  jdbc:\n    template:\n      query-timeout: 5s\n  application:').replace('server:\n','server:\n  forward-headers-strategy: none\n  max-http-request-header-size: 8KB\n').replace('  demo: false','  demo: false\n  security:\n    requests-per-minute: ${REQUESTS_PER_MINUTE:120}\n    global-requests-per-minute: ${GLOBAL_REQUESTS_PER_MINUTE:6000}');f.write_text(s)
f=p/'src/main/resources/templates/index.html';f.write_text(f.read_text().replace('onchange="this.form.requestSubmit()"','id="search-sort"'))
f=p/'src/main/resources/static/js/app.js';f.write_text(f.read_text().replace("(() => {","(() => {\n document.getElementById('search-sort')?.addEventListener('change', event => event.target.form.requestSubmit());",1))
f=p/'mvnw';s=f.read_text().replace('if command -v mvn', '''# Prefer the project-managed patched runtime over an outdated shell default.
if [ -x "$ROOT/.tools/jdk-21.0.13+9/Contents/Home/bin/java" ]; then
 export JAVA_HOME="$ROOT/.tools/jdk-21.0.13+9/Contents/Home"
 export PATH="$JAVA_HOME/bin:$PATH"
fi
if command -v mvn''');f.write_text(s)
(p/'src/main/java/kr/aptfolio/WebSecurityFilter.java').write_text('''package kr.aptfolio;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, per-process fixed-window limit. Forwarded client headers are never trusted. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class WebSecurityFilter extends OncePerRequestFilter {
 private final int perClient;
 private final int globalLimit;
 private final Map<String,Integer> counts = new HashMap<>();
 private long window = -1;
 private int total;
 public WebSecurityFilter(@Value("${app.security.requests-per-minute:120}") int perClient,
   @Value("${app.security.global-requests-per-minute:6000}") int globalLimit) {
  if(perClient < 1 || globalLimit < 1) throw new IllegalArgumentException("Request limits must be positive");
  this.perClient=perClient; this.globalLimit=globalLimit;
 }
 synchronized boolean allow(String client, long now) {
  long current=now/60000;
  if(current!=window){counts.clear();total=0;window=current;}
  int count=counts.getOrDefault(client,0);
  if(total>=globalLimit || count>=perClient || (!counts.containsKey(client)&&counts.size()>=10000))return false;
  counts.put(client,count+1); total++; return true;
 }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
 throws IOException,ServletException {
  res.setHeader("X-Content-Type-Options","nosniff");
  res.setHeader("X-Frame-Options","DENY");
  res.setHeader("Referrer-Policy","strict-origin-when-cross-origin");
  res.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=()");
  res.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
  if(req.isSecure())res.setHeader("Strict-Transport-Security","max-age=31536000");
  String path=req.getRequestURI();
  boolean search=path.equals("/")||path.equals("/api/trades")||path.startsWith("/trades/");
  if(search && !allow(req.getRemoteAddr(),System.currentTimeMillis())) {
   res.setStatus(429);res.setHeader("Retry-After","60");res.setHeader("Cache-Control","no-store");
   res.setContentType("application/json;charset=UTF-8");
   res.getWriter().write("{\\"error\\":\\"too_many_requests\\",\\"message\\":\\"잠시 후 다시 시도해주세요.\\"}");return;
  }
  chain.doFilter(req,res);
 }
}
''')
