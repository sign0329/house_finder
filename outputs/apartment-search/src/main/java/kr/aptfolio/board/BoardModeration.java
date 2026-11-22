package kr.aptfolio.board;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BoardModeration {
    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 100000;

    private final NamedParameterJdbcTemplate db;

    public BoardModeration(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    private void validateReason(String value) {
        if (value.isBlank() || value.length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사유는 1~500자로 입력하세요.");
    }

    @Transactional
    public void report(long postId, Long commentId, String username, String reason) {
        validateReason(reason);
        // Lock the reporter to serialize duplicate submissions on H2 and PostgreSQL.
        var reporterIds =
                db.queryForList(
                        "select id from user_account where username=:username for update",
                        Map.of("username", username),
                        Long.class);
        if (reporterIds.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var post =
                db.queryForList(
                        """
                        select title, body
                        from board_post
                        where id=:id
                        and hidden=false for update
                        """,
                        Map.of("id", postId));
        if (post.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String body = (String) post.getFirst().get("body");
        if (commentId != null) {
            var items =
                    db.queryForList(
                            """
                            select body
                            from board_comment
                            where id=:id
                            and post_id=:postId
                            and hidden=false for update
                            """,
                            Map.of("id", commentId, "postId", postId),
                            String.class);
            if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            body = items.getFirst();
        }
        String targetType = commentId == null ? "POST" : "COMMENT";
        long targetId = commentId == null ? postId : commentId;
        var parameters = new HashMap<String, Object>();
        parameters.put("reporter", reporterIds.getFirst());
        parameters.put("type", targetType);
        parameters.put("target", targetId);
        parameters.put("post", postId);
        parameters.put("reason", reason);
        parameters.put("title", post.getFirst().get("title"));
        parameters.put("body", body);
        db.update(
                """
                insert into board_report(reporter_id, target_type, target_id, post_id, reason,
                snapshot_title, snapshot_body) select :reporter, :type, :target, :post, :reason,
                :title, :body
                where not exists(select 1
                from board_report
                where reporter_id=:reporter
                and target_type=:type
                and target_id=:target)
                """,
                parameters);
    }

    public List<Map<String, Object>> list(String status, int page) {
        if (!Set.of("ALL", "OPEN", "HIDDEN", "RESTORED", "DISMISSED").contains(status)
                || page < 0
                || page > MAX_PAGE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return db.queryForList(
                """
                select r.*, u.nickname as reporter_name, case when r.target_type='POST' then
                p.hidden else c.hidden end as target_hidden, case when r.target_type='POST' then
                p.body else c.body end as current_body
                from board_report r
                join user_account u on u.id=r.reporter_id
                left
                join board_post p on p.id=r.target_id
                and r.target_type='POST'
                left
                join board_comment c on c.id=r.target_id
                and r.target_type='COMMENT'
                where (:status='ALL'
                or r.status=:status)
                order by r.id desc
                limit 21 offset :offset
                """,
                Map.of("status", status, "offset", (long) page * PAGE_SIZE));
    }

    public List<Map<String, Object>> history(long reportId) {
        return db.queryForList(
                """
                select *
                from board_moderation_event
                where report_id=:id
                order by id desc
                limit 20
                """,
                Map.of("id", reportId));
    }

    @Transactional
    public void decide(long reportId, String action, String note, String admin) {
        validateReason(note);
        if (!Set.of("HIDE", "RESTORE", "DISMISS").contains(action))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var reports =
                db.queryForList(
                        "select * from board_report where id=:id for update",
                        Map.of("id", reportId));
        if (reports.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var report = reports.getFirst();
        if (!action.equals("DISMISS")) {
            String table =
                    report.get("target_type").equals("POST") ? "board_post" : "board_comment";
            int rows =
                    db.update(
                            "update " + table + " set hidden=:hidden where id=:target",
                            Map.of(
                                    "hidden",
                                    action.equals("HIDE"),
                                    "target",
                                    report.get("target_id")));
            if (rows != 1)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "이미 삭제된 대상입니다. 신고 기각만 가능합니다.");
        }
        String status =
                switch (action) {
                    case "HIDE" -> "HIDDEN";
                    case "RESTORE" -> "RESTORED";
                    default -> "DISMISSED";
                };
        db.update(
                "update board_report set status=:status where id=:id",
                Map.of("status", status, "id", reportId));
        db.update(
                """
                insert into board_moderation_event(report_id, admin_username, action, note)
                values(:id, :admin, :action, :note)
                """,
                Map.of("id", reportId, "admin", admin, "action", action, "note", note));
    }
}
