package kr.aptfolio.board;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@ConditionalOnProperty(name = "collection.api.enabled", havingValue = "true")
public class BoardModerationController {
    private final BoardModeration moderation;

    public BoardModerationController(BoardModeration moderation) {
        this.moderation = moderation;
    }

    @GetMapping("/admin/board")
    public String list(
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        var items = moderation.list(status, page);
        var reports = items.stream().limit(20).toList();
        for (var report : reports)
            report.put("events", moderation.history(((Number) report.get("id")).longValue()));
        model.addAttribute("reports", reports);
        model.addAttribute("filter", status);
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() > 20);
        return "board-moderation";
    }

    @PostMapping("/admin/board/reports/{id}")
    public String decide(
            @PathVariable long id,
            @RequestParam String action,
            @RequestParam(defaultValue = "") String note,
            Principal principal) {
        moderation.decide(id, action, note, principal.getName());
        return "redirect:/admin/board";
    }
}
