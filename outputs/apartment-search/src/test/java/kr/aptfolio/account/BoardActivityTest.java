package kr.aptfolio.account;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import kr.aptfolio.board.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:activity-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class BoardActivityTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired BoardPosts posts;
    @Autowired BoardComments comments;

    @BeforeEach
    void setup() {
        db.update(
                "insert into user_account(username,nickname,password_hash)"
                        + " values('aa','회원A','unused'),('ab','회원B','unused')");
    }

    @Test
    void onlySessionOwnerActivitiesAreVisible() throws Exception {
        long a = posts.create("aa", "내 게시글", "내용");
        long b = posts.create("ab", "다른 게시글", "내용");
        comments.create(b, "aa", "내 댓글");
        comments.create(a, "ab", "다른 댓글");
        mvc.perform(get("/account/activity")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/account/activity").with(user("aa")).param("username", "ab"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("내 게시글")))
                .andExpect(content().string(not(containsString("다른 게시글"))));
        mvc.perform(get("/account/activity").with(user("aa")).param("type", "comments"))
                .andExpect(content().string(containsString("내 댓글")))
                .andExpect(content().string(not(containsString("다른 댓글"))));
    }

    @Test
    void hiddenOrDeletedContentDoesNotLeak() throws Exception {
        long id = posts.create("aa", "숨긴 글", "내용");
        comments.create(id, "aa", "숨긴 댓글");
        db.update("update board_post set hidden=true where id=?", id);
        mvc.perform(get("/account/activity").with(user("aa")))
                .andExpect(content().string(not(containsString("숨긴 글"))));
        mvc.perform(get("/account/activity").with(user("aa")).param("type", "comments"))
                .andExpect(content().string(not(containsString("숨긴 댓글"))));
        db.update("update board_post set hidden=false where id=?", id);
        db.update("update board_comment set hidden=true where post_id=?", id);
        mvc.perform(get("/account/activity").with(user("aa")).param("type", "comments"))
                .andExpect(content().string(not(containsString("숨긴 댓글"))));
        posts.delete(id, "aa");
        mvc.perform(get("/account/activity").with(user("aa")))
                .andExpect(content().string(containsString("표시할 활동이 없습니다")));
    }

    @Test
    void paginationAndValidation() throws Exception {
        long id = posts.create("ab", "대상 글", "본문");
        for (int i = 0; i < 21; i++) comments.create(id, "aa", "활동 " + i);
        mvc.perform(get("/account/activity").with(user("aa")).param("type", "comments"))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("type=comments")));
        mvc.perform(
                        get("/account/activity")
                                .with(user("aa"))
                                .param("type", "comments")
                                .param("page", "1"))
                .andExpect(content().string(containsString("활동 0")))
                .andExpect(content().string(not(containsString("활동 20"))));
        mvc.perform(get("/account/activity").with(user("aa")).param("type", "bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/account/activity").with(user("aa")).param("page", "-1"))
                .andExpect(status().isBadRequest());
    }
}
