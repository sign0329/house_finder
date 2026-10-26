package kr.aptfolio;

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
   res.getWriter().write("{\"error\":\"too_many_requests\",\"message\":\"잠시 후 다시 시도해주세요.\"}");return;
  }
  chain.doFilter(req,res);
 }
}
