package kr.aptfolio.account;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class AccountController {
    private final UserAccounts users;
    private final ProfilePhotos photos;
    private final kr.aptfolio.bookmark.Bookmarks bookmarks;

    public AccountController(
            UserAccounts users, ProfilePhotos photos, kr.aptfolio.bookmark.Bookmarks bookmarks) {
        this.photos = photos;
        this.bookmarks = bookmarks;
        this.users = users;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @PostMapping("/signup")
    public String register(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String nickname,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String confirmPassword,
            Model model,
            HttpServletResponse response) {
        try {
            users.register(username, nickname, password, confirmPassword);
            return "redirect:/login?registered";
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            model.addAttribute("error", e.getMessage());
        } catch (DuplicateKeyException e) {
            response.setStatus(409);
            model.addAttribute("error", "이미 사용 중인 아이디입니다.");
        }
        // Password fields deliberately never enter the model.
        model.addAttribute("username", username);
        model.addAttribute("nickname", nickname);
        return "signup";
    }

    @GetMapping("/api/account/session")
    @ResponseBody
    public java.util.Map<String, String> session(
            Principal principal, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return java.util.Map.of("username", principal.getName());
    }

    @GetMapping("/account")
    public String account(Principal principal, Model model) {
        model.addAttribute("profile", users.profile(principal.getName()));
        model.addAttribute("hasPhoto", photos.exists(principal.getName()));
        model.addAttribute("bookmarks", bookmarks.list(principal.getName()));
        return "account";
    }

    @PostMapping("/account/photo")
    public String uploadPhoto(
            Principal principal,
            @RequestParam("photo") org.springframework.web.multipart.MultipartFile photo,
            Model model,
            HttpServletResponse response) {
        try {
            photos.save(principal.getName(), photo);
            return "redirect:/account?photoUpdated";
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            model.addAttribute("photoError", e.getMessage());
            return account(principal, model);
        }
    }

    @GetMapping("/account/photo")
    public org.springframework.http.ResponseEntity<byte[]> photo(Principal principal) {
        byte[] bytes = photos.get(principal.getName());
        if (bytes == null)
            return org.springframework.http.ResponseEntity.notFound()
                    .header("Cache-Control", "no-store")
                    .build();
        return org.springframework.http.ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .body(bytes);
    }

    @PostMapping("/account/photo/delete")
    public String deletePhoto(Principal principal) {
        photos.delete(principal.getName());
        return "redirect:/account?photoDeleted";
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public String tooLarge() {
        return "redirect:/account?photoTooLarge";
    }
}
