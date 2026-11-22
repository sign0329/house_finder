package kr.aptfolio.account;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SocialOAuthUsers {
    private final SocialAccounts accounts;

    public SocialOAuthUsers(SocialAccounts accounts) {
        this.accounts = accounts;
    }

    public OAuth2User kakao(OAuth2UserRequest request) {
        if (!"kakao".equals(request.getClientRegistration().getRegistrationId())) throw invalid();
        var user = new DefaultOAuth2UserService().loadUser(request);
        Object id = user.getAttributes().get("id");
        if (id == null || !id.toString().matches("[1-9][0-9]*")) throw invalid();
        String username = accounts.resolve("kakao", id.toString());
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("username", username),
                "username");
    }

    public OidcUser google(OidcUserRequest request) {
        if (!"google".equals(request.getClientRegistration().getRegistrationId())) throw invalid();
        var user = new OidcUserService().loadUser(request);
        String username = accounts.resolve("google", user.getSubject());
        return new LocalOidcUser(user, username);
    }

    private static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
    }

    static final class LocalOidcUser extends DefaultOidcUser {
        private final String username;

        LocalOidcUser(OidcUser source, String username) {
            super(
                    List.of(new SimpleGrantedAuthority("ROLE_USER")),
                    source.getIdToken(),
                    source.getUserInfo());
            this.username = username;
        }

        @Override
        public String getName() {
            return username;
        }
    }
}
