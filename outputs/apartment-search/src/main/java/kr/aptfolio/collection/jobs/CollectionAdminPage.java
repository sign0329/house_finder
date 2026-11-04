package kr.aptfolio.collection.jobs;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Session-authenticated admin page and public login form. */
@Controller
@ConditionalOnProperty(name="collection.api.enabled", havingValue="true")
public class CollectionAdminPage {
 @GetMapping("/admin/login")
 public String login(HttpServletResponse response) {
  response.setHeader("Cache-Control", "no-store");
  return "admin-login";
 }
 @GetMapping("/admin")
 public String page(HttpServletResponse response) {
  response.setHeader("Cache-Control", "no-store");
  return "admin";
 }
}
