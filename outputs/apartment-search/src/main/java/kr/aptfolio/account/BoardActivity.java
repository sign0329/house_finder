package kr.aptfolio.account;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BoardActivity {
    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 100000;

    private final NamedParameterJdbcTemplate db;

    public BoardActivity(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public record Item(long postId, String title, String excerpt, LocalDateTime createdAt) {}

    public List<Item> list(String username, String type, int page) {
        if (!Set.of("posts", "comments").contains(type) || page < 0 || page > MAX_PAGE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String sql =
                type.equals("posts")
                        ? """
select p.id as post_id,p.title,substring(p.body,1,180) as excerpt,p.created_at
from board_post p
join user_account u on u.id=p.user_id
where u.username=:username
and p.hidden=false
order by p.id desc
"""
                        : """
select p.id as post_id, p.title, substring(c.body,1,180) as excerpt, c.created_at
from board_comment c
join user_account u on u.id=c.user_id
join board_post p on p.id=c.post_id
where u.username=:username
and c.hidden=false
and p.hidden=false
order by c.id desc
""";
        return db.query(
                sql + " limit 21 offset :offset",
                Map.of("username", username, "offset", (long) page * PAGE_SIZE),
                (r, n) ->
                        new Item(
                                r.getLong("post_id"),
                                r.getString("title"),
                                r.getString("excerpt"),
                                r.getTimestamp("created_at").toLocalDateTime()));
    }
}
