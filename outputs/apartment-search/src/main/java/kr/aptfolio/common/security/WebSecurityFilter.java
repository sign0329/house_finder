package kr.aptfolio.common.security;

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
 @org.springframework.beans.factory.annotation.Autowired
 private kr.aptfolio.trade.KakaoMapSettings maps;
 private final int perClient;
 private final int globalLimit;
 private final Map<String,Integer> counts = new HashMap<>();
 private long window = -1;
 private int total;
 private final Map<String,Integer> loginCounts=new HashMap<>();
 private long loginWindow=-1;
 private int loginTotal;
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
 synchronized boolean allowLogin(String client,long now) {
  long current=now/60000;
  if(current!=loginWindow){loginCounts.clear();loginTotal=0;loginWindow=current;}
  int count=loginCounts.getOrDefault(client,0);
  if(count>=10||loginTotal>=1000)return false;
  loginCounts.put(client,count+1);loginTotal++;return true;
 }
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
 throws IOException,ServletException {
  res.setHeader("X-Content-Type-Options","nosniff");
  res.setHeader("X-Frame-Options","DENY");
  res.setHeader("Referrer-Policy","strict-origin-when-cross-origin");
  res.setHeader("Permissions-Policy","camera=(), microphone=(), geolocation=()");
  res.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
  if(maps!=null && maps.enabled() && req.getRequestURI().matches("/trades/[0-9]+"))
   res.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self' https://dapi.kakao.com https://t1.daumcdn.net; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://*.daumcdn.net https://*.kakaocdn.net; font-src 'self'; connect-src 'self' https://dapi.kakao.com; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
  if(req.isSecure())res.setHeader("Strict-Transport-Security","max-age=31536000");
  String path=req.getRequestURI();
  if((path.equals("/admin/login")||path.equals("/login")||path.equals("/signup"))&&req.getMethod().equals("POST")&&!allowLogin(req.getRemoteAddr(),System.currentTimeMillis())) {
   res.setStatus(429);res.setHeader("Retry-After","60");res.setHeader("Cache-Control","no-store");
   res.setContentType("text/plain;charset=UTF-8");res.getWriter().write("인증 요청이 많습니다. 1분 후 다시 시도하세요.");return;
  }
  boolean search=path.equals("/")||path.equals("/api/trades")||path.startsWith("/trades/");
  if(search && !allow(req.getRemoteAddr(),System.currentTimeMillis())) {
   res.setStatus(429);res.setHeader("Retry-After","60");res.setHeader("Cache-Control","no-store");
   res.setContentType("application/json;charset=UTF-8");
   res.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"잠시 후 다시 시도해주세요.\"}");return;
  }
  chain.doFilter(req,res);
 }
}
