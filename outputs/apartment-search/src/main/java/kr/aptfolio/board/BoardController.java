package kr.aptfolio.board;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
@RequestMapping("/board")
public class BoardController {
    private final BoardPosts posts;

    public BoardController(BoardPosts posts) {
        this.posts = posts;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        var items = posts.list(page);
        model.addAttribute("posts", items.stream().limit(BoardPosts.PAGE_SIZE).toList());
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() > BoardPosts.PAGE_SIZE);
        return "board-list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("title", "");
        model.addAttribute("body", "");
        model.addAttribute("action", "/board");
        return "board-form";
    }

    @PostMapping
    public String create(
            Principal principal,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String body,
            Model model,
            HttpServletResponse response) {
        try {
            return "redirect:/board/" + posts.create(principal.getName(), title, body);
        } catch (IllegalArgumentException e) {
            return invalid(title, body, "/board", e, model, response);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id, Principal principal, Model model) {
        var post = posts.get(id);
        model.addAttribute("post", post);
        model.addAttribute("owner", post.username().equals(principal.getName()));
        return "board-detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Principal principal, Model model) {
        var post = posts.owned(id, principal.getName());
        model.addAttribute("title", post.title());
        model.addAttribute("body", post.body());
        model.addAttribute("action", "/board/" + id);
        return "board-form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            Principal principal,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String body,
            Model model,
            HttpServletResponse response) {
        try {
            posts.update(id, principal.getName(), title, body);
            return "redirect:/board/" + id;
        } catch (IllegalArgumentException e) {
            return invalid(title, body, "/board/" + id, e, model, response);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, Principal principal) {
        posts.delete(id, principal.getName());
        return "redirect:/board";
    }

    private String invalid(
            String title,
            String body,
            String action,
            IllegalArgumentException error,
            Model model,
            HttpServletResponse response) {
        response.setStatus(400);
        model.addAttribute("title", title);
        model.addAttribute("body", body);
        model.addAttribute("action", action);
        model.addAttribute("error", error.getMessage());
        return "board-form";
    }
}
