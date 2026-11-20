package kr.aptfolio.notification;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/account/alerts")
public class PriceAlertController {
    private final PriceAlerts alerts;

    public PriceAlertController(PriceAlerts alerts) {
        this.alerts = alerts;
    }

    @GetMapping
    public String page(
            Principal principal, @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("settings", alerts.settings(principal.getName()));
        model.addAttribute("notifications", alerts.list(principal.getName(), page));
        return "price-alerts";
    }

    @PostMapping("/{id}/target")
    public String configure(Principal principal, @PathVariable long id, @RequestParam long price) {
        alerts.configure(principal.getName(), id, price);
        return "redirect:/account/alerts?updated";
    }

    @PostMapping("/{id}/disable")
    public String disable(Principal principal, @PathVariable long id) {
        alerts.disable(principal.getName(), id);
        return "redirect:/account/alerts?disabled";
    }

    @PostMapping("/read/{id}")
    public String read(Principal principal, @PathVariable long id) {
        alerts.markRead(principal.getName(), id);
        return "redirect:/account/alerts";
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> missing() {
        return ResponseEntity.status(404).body("알림 또는 관심 아파트를 찾을 수 없습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
