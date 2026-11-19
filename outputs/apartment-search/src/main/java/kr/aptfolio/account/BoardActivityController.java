package kr.aptfolio.account;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class BoardActivityController {
    private final BoardActivity activity;

    public BoardActivityController(BoardActivity activity) {
        this.activity = activity;
    }

    @GetMapping("/account/activity")
    public String list(
            Principal principal,
            @RequestParam(defaultValue = "posts") String type,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        var items = activity.list(principal.getName(), type, page);
        model.addAttribute("items", items.stream().limit(20).toList());
        model.addAttribute("hasNext", items.size() > 20);
        model.addAttribute("type", type);
        model.addAttribute("page", page);
        return "account-activity";
    }
}
