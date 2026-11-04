package kr.aptfolio.account;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class AccountNavigation {
 @ModelAttribute public void navigation(Authentication authentication,Model model) {
  boolean user=authentication!=null&&authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_USER"));
  boolean admin=authentication!=null&&authentication.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN"));
  model.addAttribute("userSignedIn",user);model.addAttribute("adminSignedIn",admin);
 }
}
