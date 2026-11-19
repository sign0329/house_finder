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
                            r.getTimestamp("created_at").toLocalDateTime(),
                            r.getTimestamp("updated_at").toLocalDateTime());

    public List<Post> list(int page) {
        if (page < 0 || page > 100000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 범위를 확인하세요.");
        return db.query(
                SELECT + " where p.hidden=false order by p.id desc limit :limit offset :offset",
                Map.of("limit", PAGE_SIZE + 1, "offset", (long) page * PAGE_SIZE),
                mapper);
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
        title = title.strip();
        body = body.strip();
        validate(title, body);
        var key = new GeneratedKeyHolder();
        int rows =
                db.update(
                        "insert into board_post(user_id,title,body) select id,:title,:body from"
                                + " user_account where username=:username",
                        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(
                                Map.of("username", username, "title", title, "body", body)),
                        key,
                        new String[] {"id"});
        if (rows != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public void update(long id, String username, String title, String body) {
        owned(id, username);
        title = title.strip();
        body = body.strip();
        validate(title, body);
        int rows =
                db.update(
                        "update board_post set title=:title,body=:body,updated_at=CURRENT_TIMESTAMP"
                                + " where id=:id and hidden=false and user_id=(select id from"
                                + " user_account where username=:username)",
                        Map.of("id", id, "username", username, "title", title, "body", body));
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
