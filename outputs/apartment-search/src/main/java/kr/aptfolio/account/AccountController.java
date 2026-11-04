package kr.aptfolio.account;

import java.security.Principal;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AccountController {
 private final UserAccounts users;
 public AccountController(UserAccounts users){this.users=users;}
 @GetMapping("/login") public String login(){return "login";}
 @GetMapping("/signup") public String signup(){return "signup";}
 @PostMapping("/signup") public String register(@RequestParam(defaultValue="") String username,
   @RequestParam(defaultValue="") String nickname,@RequestParam(defaultValue="") String password,
   @RequestParam(defaultValue="") String confirmPassword,Model model,HttpServletResponse response) {
  try {users.register(username,nickname,password,confirmPassword);return "redirect:/login?registered";}
  catch(IllegalArgumentException e){response.setStatus(400);model.addAttribute("error",e.getMessage());}
  catch(DuplicateKeyException e){response.setStatus(409);model.addAttribute("error","이미 사용 중인 아이디입니다.");}
  // Password fields deliberately never enter the model.
  model.addAttribute("username",username);model.addAttribute("nickname",nickname);return "signup";
 }
 @GetMapping("/account") public String account(Principal principal,Model model) {
  model.addAttribute("profile",users.profile(principal.getName()));return "account";
 }
}
