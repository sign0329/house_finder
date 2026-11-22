package kr.aptfolio.board;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequestMapping("/board")
public class BoardController {
    private final BoardPosts posts;
    private final BoardComments comments;

    public BoardController(BoardPosts posts, BoardComments comments) {
        this.comments = comments;
        this.posts = posts;
    }

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "ALL") String scope,
            Model model) {
        var items = posts.list(page, q, category, scope);
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("scope", scope);
        model.addAttribute("posts", items.stream().limit(BoardPosts.PAGE_SIZE).toList());
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() > BoardPosts.PAGE_SIZE);
        return "board-list";
    }

    @GetMapping("/new")
    public String form(Model model) {
        model.addAttribute("category", "FREE");
        model.addAttribute("title", "");
        model.addAttribute("body", "");
        model.addAttribute("action", "/board");
        return "board-form";
    }

    @PostMapping
    public String create(
            Principal principal,
            @RequestParam(defaultValue = "FREE") String category,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String body,
            Model model,
            HttpServletResponse response) {
        try {
            return "redirect:/board/" + posts.create(principal.getName(), title, body, category);
        } catch (IllegalArgumentException e) {
            model.addAttribute("category", category);
            return invalid(title, body, "/board", e, model, response);
        }
    }

    @GetMapping("/{id}")
    public String detail(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int commentPage,
            Principal principal,
            Model model) {
        var post = posts.get(id);
        model.addAttribute("post", post);
        model.addAttribute("owner", post.username().equals(principal.getName()));
        var items = comments.list(id, commentPage);
        model.addAttribute("comments", items.stream().limit(20).toList());
        model.addAttribute("commentPage", commentPage);
        model.addAttribute("commentsNext", items.size() > 20);
        model.addAttribute("viewer", principal.getName());
        return "board-detail";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable long id, Principal principal, Model model) {
        var post = posts.owned(id, principal.getName());
        model.addAttribute("category", post.category());
        model.addAttribute("title", post.title());
        model.addAttribute("body", post.body());
        model.addAttribute("action", "/board/" + id);
        return "board-form";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable long id,
            Principal principal,
            @RequestParam(defaultValue = "FREE") String category,
            @RequestParam(defaultValue = "") String title,
            @RequestParam(defaultValue = "") String body,
            Model model,
            HttpServletResponse response) {
        try {
            posts.update(id, principal.getName(), title, body, category);
            return "redirect:/board/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("category", category);
            return invalid(title, body, "/board/" + id, e, model, response);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, Principal principal) {
        posts.delete(id, principal.getName());
        return "redirect:/board";
    }

    @PostMapping("/{id}/comments")
    public String comment(
            @PathVariable long id,
            Principal principal,
            @RequestParam(defaultValue = "") String body,
            Model model,
            HttpServletResponse response) {
        try {
            comments.create(id, principal.getName(), body);
            return "redirect:/board/" + id + "#comments";
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            model.addAttribute("commentError", e.getMessage());
            model.addAttribute("commentBody", body);
            return detail(id, 0, principal, model);
        }
    }

    @PostMapping("/{id}/comments/{commentId}/delete")
    public String deleteComment(
            @PathVariable long id, @PathVariable long commentId, Principal principal) {
        comments.delete(id, commentId, principal.getName());
        return "redirect:/board/" + id + "#comments";
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
