package kr.aptfolio.account;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:social-login;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "GOOGLE_LOGIN_ENABLED=true",
            "GOOGLE_CLIENT_ID=test-google",
            "GOOGLE_CLIENT_SECRET=test-secret",
            "KAKAO_LOGIN_ENABLED=true",
            "KAKAO_CLIENT_ID=test-kakao",
            "KAKAO_CLIENT_SECRET=test-secret"
        })
@AutoConfigureMockMvc
class SocialLoginTest {
    @Autowired MockMvc mvc;
    @Autowired SocialAccounts accounts;
    @Autowired UserAccounts users;
    @Autowired JdbcTemplate db;
    static final RSAKey KEY;
    static final HttpServer SERVER;
    static String nonce;
    static boolean wrongNonce;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("fixture").generate();
            SERVER = HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext(
                    "/",
                    exchange -> {
                        try {
                            String path = exchange.getRequestURI().getPath();
                            String body;
                            if (path.equals("/jwks"))
                                body = new JWKSet(KEY.toPublicJWK()).toString();
                            else if (path.equals("/kakao-user")) body = "{\"id\":123456}";
                            else if (path.equals("/google-user"))
                                body = "{\"sub\":\"google-subject\"}";
                            else if (path.equals("/google-token")) {
                                var claims =
                                        new JWTClaimsSet.Builder()
                                                .issuer("https://accounts.google.com")
                                                .audience("test-google")
                                                .subject("google-subject")
                                                .issueTime(Date.from(Instant.now()))
                                                .expirationTime(
                                                        Date.from(Instant.now().plusSeconds(300)))
                                                .claim("nonce", wrongNonce ? "wrong-nonce" : nonce)
                                                .build();
                                var jwt =
                                        new SignedJWT(
                                                new JWSHeader.Builder(JWSAlgorithm.RS256)
                                                        .keyID("fixture")
                                                        .build(),
                                                claims);
                                jwt.sign(new RSASSASigner(KEY));
                                body =
                                        "{\"access_token\":\"fixture\",\"token_type\":\"Bearer\",\"expires_in\":300,\"id_token\":\""
                                                + jwt.serialize()
                                                + "\"}";
                            } else
                                body =
                                        "{\"access_token\":\"fixture\",\"token_type\":\"Bearer\",\"expires_in\":300}";
                            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                            exchange.sendResponseHeaders(200, bytes.length);
                            exchange.getResponseBody().write(bytes);
                        } catch (Exception ex) {
                            throw new java.io.IOException(ex);
                        } finally {
                            exchange.close();
                        }
                    });
            SERVER.start();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @AfterAll
    static void closeServer() {
        SERVER.stop(0);
    }

    @TestConfiguration
    static class MockProvider {
        @Bean
        @Primary
        ClientRegistrationRepository mockClients(
                @Qualifier("socialClients") ClientRegistrationRepository real) {
            String base = "http://127.0.0.1:" + SERVER.getAddress().getPort();
            return new InMemoryClientRegistrationRepository(
                    ClientRegistration.withClientRegistration(real.findByRegistrationId("google"))
                            .tokenUri(base + "/google-token")
                            .userInfoUri(base + "/google-user")
                            .jwkSetUri(base + "/jwks")
                            .build(),
                    ClientRegistration.withClientRegistration(real.findByRegistrationId("kakao"))
                            .tokenUri(base + "/kakao-token")
                            .userInfoUri(base + "/kakao-user")
                            .build());
        }
    }

    private static Map<String, String> query(String url) {
        var values = new HashMap<String, String>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    @Test
    void forgedCallbackCannotCreateAccountOrAuthenticate() throws Exception {
        long before = db.queryForObject("select count(*) from user_account", Long.class);
        for (String provider : List.of("google", "kakao")) {
            mvc.perform(
                            get("/login/oauth2/code/" + provider)
                                    .param("code", "forged")
                                    .param("state", "forged"))
                    .andExpect(redirectedUrl("/login?socialError"))
                    .andExpect(unauthenticated());
        }
        var start = mvc.perform(get("/oauth2/authorization/google")).andReturn();
        var values = query(start.getResponse().getRedirectedUrl());
        nonce = values.get("nonce");
        wrongNonce = true;
        try {
            mvc.perform(
                            get("/login/oauth2/code/google")
                                    .session((MockHttpSession) start.getRequest().getSession(false))
                                    .param("code", "fixture")
                                    .param("state", values.get("state")))
                    .andExpect(redirectedUrl("/login?socialError"))
                    .andExpect(unauthenticated());
        } finally {
            wrongNonce = false;
        }
        assertEquals(before, db.queryForObject("select count(*) from user_account", Long.class));
    }

    @Test
    void mockGoogleAndKakaoCodeFlowsCreateUsableStableUserSessions() throws Exception {
        for (String provider : List.of("google", "kakao")) {
            String previous = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                var start =
                        mvc.perform(get("/oauth2/authorization/" + provider))
                                .andExpect(status().is3xxRedirection())
                                .andReturn();
                var values = query(start.getResponse().getRedirectedUrl());
                assertNotNull(values.get("state"));
                assertEquals(
                        "http://localhost:8082/login/oauth2/code/" + provider,
                        values.get("redirect_uri"));
                nonce = values.get("nonce");
                var session = (MockHttpSession) start.getRequest().getSession(false);
                mvc.perform(
                                get("/login/oauth2/code/" + provider)
                                        .session(session)
                                        .param("code", "fixture")
                                        .param("state", values.get("state")))
                        .andExpect(redirectedUrl("/"))
                        .andExpect(authenticated().withRoles("USER"));
                String username =
                        db.queryForObject(
                                "select u.username from user_account u join social_identity s on"
                                        + " s.user_id=u.id where s.provider=? and s.subject=?",
                                String.class,
                                provider,
                                provider.equals("google") ? "google-subject" : "123456");
                if (previous != null) assertEquals(previous, username);
                previous = username;
                mvc.perform(get("/account").session(session)).andExpect(status().isOk());
                mvc.perform(get("/api/account/bookmarks").session(session))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.username").value(username));
                mvc.perform(get("/account/alerts").session(session)).andExpect(status().isOk());
            }
        }
    }

    @Test
    void concurrentFirstLoginIsUniqueAndProviderIdentityNeverMerges() throws Exception {
        String subject = "concurrent-subject";
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<String> login =
                    () -> {
                        gate.await();
                        return accounts.resolve("google", subject);
                    };
            var a = pool.submit(login);
            var b = pool.submit(login);
            gate.countDown();
            String username = a.get(10, TimeUnit.SECONDS);
            assertEquals(username, b.get(10, TimeUnit.SECONDS));
            assertFalse(users.loadUserByUsername(username).isEnabled());
            assertNotEquals(username, accounts.resolve("kakao", subject));
        }
        assertThrows(IllegalArgumentException.class, () -> accounts.resolve("unknown", "x"));
        assertThrows(IllegalArgumentException.class, () -> accounts.resolve("google", ""));
    }

    @Test
    void configuredProvidersAppearOnLogin() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/oauth2/authorization/google")))
                .andExpect(content().string(containsString("/oauth2/authorization/kakao")));
    }
}
