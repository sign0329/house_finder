package kr.aptfolio.account;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class SocialAccounts {
    private final NamedParameterJdbcTemplate db;

    public SocialAccounts(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    @Transactional
    public String resolve(String provider, String subject) {
        if (!("google".equals(provider) || "kakao".equals(provider))
                || subject == null
                || subject.isBlank()
                || subject.length() > 255) throw new IllegalArgumentException("잘못된 소셜 계정입니다.");
        // Serializes first-login creation without catching a unique violation in a failed
        // transaction.
        db.queryForObject(
                "select id from social_registration_lock where id=1 for update",
                Map.of(),
                Integer.class);
        var key = Map.of("provider", provider, "subject", subject);
        var existing =
                db.query(
                        """
select u.username from social_identity s join user_account u on u.id=s.user_id
where s.provider=:provider and s.subject=:subject
""",
                        key,
                        (r, n) -> r.getString(1));
        if (!existing.isEmpty()) return existing.getFirst();
        // ':' cannot be registered by password signup. Never link accounts by nickname or email.
        String username = "oauth:" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        db.update(
                """
insert into user_account(username,nickname,password_hash) values(:username,:nickname,'!SOCIAL')
""",
                Map.of(
                        "username",
                        username,
                        "nickname",
                        provider.equals("google") ? "구글 회원" : "카카오 회원"));
        db.update(
                """
                insert into social_identity(provider,subject,user_id)
                select :provider,:subject,id from user_account where username=:username
                """,
                Map.of("provider", provider, "subject", subject, "username", username));
        return username;
    }
}
