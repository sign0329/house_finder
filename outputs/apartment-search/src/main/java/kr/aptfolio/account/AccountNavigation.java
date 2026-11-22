package kr.aptfolio.account;

import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice
public class AccountNavigation {
    private final org.springframework.beans.factory.ObjectProvider<
                    org.springframework.security.oauth2.client.registration
                            .ClientRegistrationRepository>
            registrations;

    public AccountNavigation(
            org.springframework.beans.factory.ObjectProvider<
                            org.springframework.security.oauth2.client.registration
                                    .ClientRegistrationRepository>
                    registrations) {
        this.registrations = registrations;
    }

    @ModelAttribute
    public void navigation(Authentication authentication, Model model) {
        var clients = registrations.getIfAvailable();
        model.addAttribute(
                "googleLoginEnabled",
                clients != null && clients.findByRegistrationId("google") != null);
        model.addAttribute(
                "kakaoLoginEnabled",
                clients != null && clients.findByRegistrationId("kakao") != null);
        boolean user =
                authentication != null
                        && authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        boolean admin =
                authentication != null
                        && authentication.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        model.addAttribute("bookmarkUser", user ? authentication.getName() : "");
        model.addAttribute("userSignedIn", user);
        model.addAttribute("adminSignedIn", admin);
    }
}
