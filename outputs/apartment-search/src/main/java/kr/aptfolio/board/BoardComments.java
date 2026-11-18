package kr.aptfolio.board;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BoardComments {
    private final NamedParameterJdbcTemplate db;
    private final BoardPosts posts;

    public BoardComments(NamedParameterJdbcTemplate db, BoardPosts posts) {
        this.db = db;
        this.posts = posts;
    }

    public record Comment(
            long id, String username, String nickname, String body, LocalDateTime createdAt) {}

    public List<Comment> list(long postId, int page) {
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return db.query(
                "select c.id,u.username,u.nickname,c.body,c.created_at from board_comment c join"
                    + " user_account u on u.id=c.user_id where c.post_id=:postId order by c.id desc"
                    + " limit 21 offset :offset",
                Map.of("postId", postId, "offset", (long) page * 20),
                (r, n) ->
                        new Comment(
                                r.getLong("id"),
                                r.getString("username"),
                                r.getString("nickname"),
                                r.getString("body"),
                                r.getTimestamp("created_at").toLocalDateTime()));
    }

    public void create(long postId, String username, String body) {
        posts.get(postId);
        body = body.strip();
        if (body.isBlank() || body.length() > 2000)
            throw new IllegalArgumentException("댓글은 1~2,000자로 입력하세요.");
        int rows =
                db.update(
                        "insert into board_comment(post_id,user_id,body) select :postId,id,:body"
                                + " from user_account where username=:username",
                        Map.of("postId", postId, "username", username, "body", body));
        if (rows != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    public void delete(long postId, long id, String username) {
        var authors =
                db.queryForList(
                        "select u.username from board_comment c join user_account u on"
                                + " u.id=c.user_id where c.id=:id and c.post_id=:postId",
                        Map.of("id", id, "postId", postId),
                        String.class);
        if (authors.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!authors.getFirst().equals(username))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        db.update(
                "delete from board_comment where id=:id and post_id=:postId and user_id=(select id"
                        + " from user_account where username=:username)",
                Map.of("id", id, "postId", postId, "username", username));
    }
}
