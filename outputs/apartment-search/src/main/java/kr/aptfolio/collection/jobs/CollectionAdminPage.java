package kr.aptfolio.collection.jobs;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Public shell only; all collection data/actions require the existing API token. */
@Controller
@ConditionalOnProperty(name="collection.api.enabled", havingValue="true")
public class CollectionAdminPage {
 @GetMapping("/admin")
 public String page(HttpServletResponse response) {
  response.setHeader("Cache-Control", "no-store");
  return "admin";
 }
}
