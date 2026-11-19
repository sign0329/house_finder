package kr.aptfolio.board;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BoardPosts {
    public static final int PAGE_SIZE = 20;
    private final NamedParameterJdbcTemplate db;

    public BoardPosts(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public record Post(
            long id,
            String username,
            String nickname,
            String title,
            String body,
            String category,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    private static final String SELECT =
            "select p.*,u.username,u.nickname from board_post p join user_account u on"
                    + " u.id=p.user_id";
    private final org.springframework.jdbc.core.RowMapper<Post> mapper =
            (r, n) ->
                    new Post(
                            r.getLong("id"),
                            r.getString("username"),
                            r.getString("nickname"),
                            r.getString("title"),
                            r.getString("body"),
                            r.getString("category"),
                            r.getTimestamp("created_at").toLocalDateTime(),
                            r.getTimestamp("updated_at").toLocalDateTime());

    public List<Post> list(int page) {
        return list(page, "", "ALL", "ALL");
    }

    public List<Post> list(int page, String query, String category, String scope) {
        if (page < 0
                || page > 100000
                || query.length() > 100
                || !Set.of("ALL", "FREE", "QUESTION", "INFO").contains(category)
                || !Set.of("ALL", "TITLE", "BODY").contains(scope))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색 조건을 확인하세요.");
        String pattern =
                "%"
                        + query.strip()
                                .toLowerCase(Locale.ROOT)
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_")
                        + "%";
        return db.query(
                SELECT
                        + " where p.hidden=false and (:category='ALL' or p.category=:category) and"
                        + " ((:scope<>'BODY' and lower(p.title) like :pattern escape '!') or"
                        + " (:scope<>'TITLE' and lower(p.body) like :pattern escape '!')) order by"
                        + " p.id desc limit :limit offset :offset",
                Map.of(
                        "category",
                        category,
                        "scope",
                        scope,
                        "pattern",
                        pattern,
                        "limit",
                        PAGE_SIZE + 1,
                        "offset",
                        (long) page * PAGE_SIZE),
                mapper);
    }

    private void validateCategory(String category) {
        if (!Set.of("FREE", "QUESTION", "INFO").contains(category))
            throw new IllegalArgumentException("질문·정보·자유 중 분류를 선택하세요.");
    }

    public Post get(long id) {
        return db
                .query(SELECT + " where p.id=:id and p.hidden=false", Map.of("id", id), mapper)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Post owned(long id, String username) {
        var post = get(id);
        if (!post.username().equals(username))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return post;
    }

    private void validate(String title, String body) {
        if (title.isBlank() || title.length() > 120 || body.isBlank() || body.length() > 10000)
            throw new IllegalArgumentException("제목은 1~120자, 내용은 1~10,000자로 입력하세요.");
    }

    public long create(String username, String title, String body) {
        return create(username, title, body, "FREE");
    }

    public long create(String username, String title, String body, String category) {
        validateCategory(category);
        title = title.strip();
        body = body.strip();
        validate(title, body);
        var key = new GeneratedKeyHolder();
        int rows =
                db.update(
                        "insert into board_post(user_id,title,body,category) select"
                                + " id,:title,:body,:category from user_account where"
                                + " username=:username",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                                Map.of(
                                        "username",
                                        username,
                                        "title",
                                        title,
                                        "body",
                                        body,
                                        "category",
                                        category)),
                        key,
                        new String[] {"id"});
        if (rows != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public void update(long id, String username, String title, String body) {
        update(id, username, title, body, owned(id, username).category());
    }

    public void update(long id, String username, String title, String body, String category) {
        validateCategory(category);
        owned(id, username);
        title = title.strip();
        body = body.strip();
        validate(title, body);
        int rows =
                db.update(
                        "update board_post set"
                            + " title=:title,body=:body,category=:category,updated_at=CURRENT_TIMESTAMP"
                            + " where id=:id and hidden=false and user_id=(select id from"
                            + " user_account where username=:username)",
                        Map.of(
                                "id",
                                id,
                                "username",
                                username,
                                "title",
                                title,
                                "body",
                                body,
                                "category",
                                category));
        if (rows != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public void delete(long id, String username) {
        owned(id, username);
        db.update(
                "delete from board_post where id=:id and hidden=false and user_id=(select id from"
                        + " user_account where username=:username)",
                Map.of("id", id, "username", username));
    }
}
