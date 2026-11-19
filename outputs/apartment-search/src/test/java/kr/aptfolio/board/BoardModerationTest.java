package kr.aptfolio.board;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties = {
            "collection.api.enabled=true",
            "ADMIN_USERNAME=operator",
            "COLLECTION_ADMIN_TOKEN=test-legacy-token-0123456789012345",
            "spring.datasource.url=jdbc:h2:mem:moderation-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
        })
@AutoConfigureMockMvc
@Transactional
class BoardModerationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired BoardPosts posts;
    @Autowired BoardComments comments;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry r) {
        r.add(
                "ADMIN_PASSWORD_HASH",
                () ->
                        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10)
                                .encode("test-admin-password"));
    }

    long id;

    @BeforeEach
    void setup() {
        db.update(
                "insert into user_account(username,nickname,password_hash)"
                        + " values('ma','작성자','unused'),('mb','신고자','unused')");
        id = posts.create("ma", "검토 제목", "검토 본문");
    }

    long report(String path) throws Exception {
        mvc.perform(
                        post(path)
                                .with(user("mb"))
                                .with(csrf())
                                .param("reason", "<script>신고 사유</script>"))
                .andExpect(status().is3xxRedirection());
        return db.queryForObject("select max(id) from board_report", Long.class);
    }

    void decision(long report, String action) throws Exception {
        mvc.perform(
                        post("/admin/board/reports/" + report)
                                .with(user("operator").roles("ADMIN"))
                                .with(csrf())
                                .param("action", action)
                                .param("note", "검토 완료"))
                .andExpect(redirectedUrl("/admin/board"));
    }

    @Test
    void reportingRequiresMemberReasonAndPreventsDuplicates() throws Exception {
        mvc.perform(post("/board/" + id + "/report").with(csrf()).param("reason", "신고"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/board/" + id + "/report").with(user("mb")).param("reason", "신고"))
                .andExpect(status().isForbidden());
        for (String reason : new String[] {" ", "가".repeat(501)})
            mvc.perform(
                            post("/board/" + id + "/report")
                                    .with(user("mb"))
                                    .with(csrf())
                                    .param("reason", reason))
                    .andExpect(status().isBadRequest());
        report("/board/" + id + "/report");
        report("/board/" + id + "/report");
        assertEquals(1, db.queryForObject("select count(*) from board_report", Integer.class));
        mvc.perform(get("/admin/board").with(user("operator").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;신고 사유&lt;/script&gt;")));
    }

    @Test
    void onlyAdminCanModerateAndCsrfIsRequired() throws Exception {
        long r = report("/board/" + id + "/report");
        mvc.perform(get("/admin/board")).andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/admin/board").with(user("mb"))).andExpect(status().isForbidden());
        mvc.perform(
                        post("/admin/board/reports/" + r)
                                .with(user("mb"))
                                .with(csrf())
                                .param("action", "HIDE")
                                .param("note", "사유"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/admin/board/reports/" + r)
                                .with(user("operator").roles("ADMIN"))
                                .param("action", "HIDE")
                                .param("note", "사유"))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidingPostBlocksReadEditCommentAndRestoreKeepsContent() throws Exception {
        long r = report("/board/" + id + "/report");
        decision(r, "HIDE");
        mvc.perform(get("/board").with(user("mb")))
                .andExpect(content().string(not(containsString("검토 제목"))));
        mvc.perform(get("/board/" + id).with(user("ma"))).andExpect(status().isNotFound());
        mvc.perform(get("/board/" + id + "/edit").with(user("ma")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/board/" + id + "/comments")
                                .with(user("mb"))
                                .with(csrf())
                                .param("body", "댓글"))
                .andExpect(status().isNotFound());
        decision(r, "RESTORE");
        mvc.perform(get("/board/" + id).with(user("mb")))
                .andExpect(content().string(containsString("검토 본문")));
        assertEquals(
                2, db.queryForObject("select count(*) from board_moderation_event", Integer.class));
    }

    @Test
    void commentHideAndDeletedTargetRetainReportHistory() throws Exception {
        comments.create(id, "ma", "숨길 댓글");
        long c = comments.list(id, 0).getFirst().id();
        long r = report("/board/" + id + "/comments/" + c + "/report");
        decision(r, "HIDE");
        mvc.perform(get("/board/" + id).with(user("mb")))
                .andExpect(content().string(not(containsString("숨길 댓글"))));
        decision(r, "RESTORE");
        assertEquals(1, comments.list(id, 0).size());
        posts.delete(id, "ma");
        decision(r, "DISMISS");
        mvc.perform(get("/admin/board").with(user("operator").roles("ADMIN")))
                .andExpect(content().string(containsString("숨길 댓글")));
        mvc.perform(
                        post("/admin/board/reports/" + r)
                                .with(user("operator").roles("ADMIN"))
                                .with(csrf())
                                .param("action", "RESTORE")
                                .param("note", "확인"))
                .andExpect(status().isConflict());
    }
}
