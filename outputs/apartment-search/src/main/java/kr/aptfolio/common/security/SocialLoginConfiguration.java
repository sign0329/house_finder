package kr.aptfolio.common.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.net.URI;
import java.util.ArrayList;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnExpression("${GOOGLE_LOGIN_ENABLED:false} or ${KAKAO_LOGIN_ENABLED:false}")
public class SocialLoginConfiguration {
    @Bean
    ClientRegistrationRepository socialClients(Environment env) {
        String base = env.getProperty("SOCIAL_LOGIN_BASE_URL", "http://localhost:8082");
        URI uri = URI.create(base);
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))
                || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme())))
            throw new IllegalArgumentException(
                    "SOCIAL_LOGIN_BASE_URL은 HTTPS 사이트 주소 또는 로컬 HTTP 주소여야 합니다.");
        base = base.replaceAll("/$", "");
        var registrations = new ArrayList<ClientRegistration>();
        if (env.getProperty("GOOGLE_LOGIN_ENABLED", Boolean.class, false))
            registrations.add(
                    CommonOAuth2Provider.GOOGLE
                            .getBuilder("google")
                            .clientId(required(env, "GOOGLE_CLIENT_ID"))
                            .clientSecret(required(env, "GOOGLE_CLIENT_SECRET"))
                            .scope("openid", "profile")
                            .redirectUri(base + "/login/oauth2/code/google")
                            .build());
        if (env.getProperty("KAKAO_LOGIN_ENABLED", Boolean.class, false))
            registrations.add(
                    ClientRegistration.withRegistrationId("kakao")
                            .clientName("카카오")
                            .clientId(required(env, "KAKAO_CLIENT_ID"))
                            .clientSecret(required(env, "KAKAO_CLIENT_SECRET"))
                            .clientAuthenticationMethod(
                                    ClientAuthenticationMethod.CLIENT_SECRET_POST)
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri(base + "/login/oauth2/code/kakao")
                            .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                            .tokenUri("https://kauth.kakao.com/oauth/token")
                            .userInfoUri("https://kapi.kakao.com/v2/user/me")
                            .userNameAttributeName("id")
                            .build());
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private static String required(Environment env, String name) {
        String value = env.getProperty(name, "").strip();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " 설정이 필요합니다.");
        return value;
    }
}
