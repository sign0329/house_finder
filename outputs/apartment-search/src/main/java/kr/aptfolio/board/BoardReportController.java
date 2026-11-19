package kr.aptfolio.board;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class BoardReportController {
    private final BoardModeration moderation;

    public BoardReportController(BoardModeration moderation) {
        this.moderation = moderation;
    }

    @PostMapping("/board/{id}/report")
    public String report(
            @PathVariable long id,
            @RequestParam(defaultValue = "") String reason,
            Principal principal) {
        moderation.report(id, null, principal.getName(), reason);
        return "redirect:/board/" + id + "?reported";
    }

    @PostMapping("/board/{id}/comments/{commentId}/report")
    public String reportComment(
            @PathVariable long id,
            @PathVariable long commentId,
            @RequestParam(defaultValue = "") String reason,
            Principal principal) {
        moderation.report(id, commentId, principal.getName(), reason);
        return "redirect:/board/" + id + "?reported#comments";
    }
}
