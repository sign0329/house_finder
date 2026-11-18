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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:comments-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class BoardCommentsTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired BoardPosts posts;
    long id;

    @BeforeEach
    void setup() {
        db.update(
                "insert into user_account(username,nickname,password_hash)"
                        + " values('ca','작성자','unused'),('cb','댓글회원','unused')");
        id = posts.create("ca", "제목", "내용");
    }

    void add(String body) throws Exception {
        mvc.perform(
                        post("/board/" + id + "/comments")
                                .with(user("cb"))
                                .with(csrf())
                                .param("body", body))
                .andExpect(redirectedUrl("/board/" + id + "#comments"));
    }

    @Test
    void memberCommentIsVisibleAndEscaped() throws Exception {
        add("<script>alert(1)</script>");
        mvc.perform(get("/board/" + id).with(user("ca")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("댓글회원")))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    @Test
    void anonymousAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(post("/board/" + id + "/comments").with(csrf()).param("body", "댓글"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/board/" + id + "/comments").with(user("cb")).param("body", "댓글"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidBodyAndMissingPostAreRejected() throws Exception {
        add("가".repeat(500));
        for (String body : new String[] {" ", "가".repeat(501)})
            mvc.perform(
                            post("/board/" + id + "/comments")
                                    .with(user("cb"))
                                    .with(csrf())
                                    .param("body", body))
                    .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/board/999999/comments")
                                .with(user("cb"))
                                .with(csrf())
                                .param("body", "댓글"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletionChecksAuthorAndPostAndCascades() throws Exception {
        add("댓글");
        long comment = db.queryForObject("select id from board_comment", Long.class);
        mvc.perform(
                        post("/board/" + id + "/comments/" + comment + "/delete")
                                .with(user("ca"))
                                .with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/board/999999/comments/" + comment + "/delete")
                                .with(user("cb"))
                                .with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/board/" + id + "/comments/" + comment + "/delete")
                                .with(user("cb"))
                                .with(csrf()))
                .andExpect(status().is3xxRedirection());
        add("남은 댓글");
        posts.delete(id, "ca");
        assertEquals(0, db.queryForObject("select count(*) from board_comment", Integer.class));
    }
}
