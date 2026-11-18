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
                "spring.datasource.url=jdbc:h2:mem:board-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class BoardTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;

    @BeforeEach
    void accounts() {
        db.update(
                "insert into user_account(username,nickname,password_hash)"
                        + " values('boardalice','작성자','unused'),('boardbob','다른 회원','unused')");
    }

    long create() throws Exception {
        var result =
                mvc.perform(
                                post("/board")
                                        .with(user("boardalice"))
                                        .with(csrf())
                                        .param("title", "우리 동네")
                                        .param("body", "<script>alert(1)</script>"))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();
        return Long.parseLong(result.getResponse().getRedirectedUrl().replace("/board/", ""));
    }

    @Test
    void onlyMembersCanReadAndWrite() throws Exception {
        for (var path : new String[] {"/board", "/board/new", "/board/1", "/board/1/edit"})
            mvc.perform(get(path)).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/board").with(user("administrator").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/board").with(csrf()).param("title", "제목").param("body", "내용"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void memberCreatesReadsAndEscapesHtml() throws Exception {
        long id = create();
        mvc.perform(get("/board").with(user("boardbob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("우리 동네")));
        mvc.perform(get("/board/" + id).with(user("boardbob")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void onlyAuthorCanEditAndDelete() throws Exception {
        long id = create();
        mvc.perform(get("/board/" + id + "/edit").with(user("boardbob")))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/board/" + id)
                                .with(user("boardbob"))
                                .with(csrf())
                                .param("title", "변조")
                                .param("body", "변조"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/board/" + id + "/delete").with(user("boardbob")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/board/" + id)
                                .with(user("boardalice"))
                                .with(csrf())
                                .param("title", "수정 제목")
                                .param("body", "수정 내용"))
                .andExpect(redirectedUrl("/board/" + id));
        mvc.perform(get("/board/" + id).with(user("boardalice")))
                .andExpect(content().string(containsString("수정 제목")));
        mvc.perform(post("/board/" + id + "/delete").with(user("boardalice")).with(csrf()))
                .andExpect(redirectedUrl("/board"));
        mvc.perform(get("/board/" + id).with(user("boardalice"))).andExpect(status().isNotFound());
    }

    @Test
    void validationCsrfAndPaginationBoundaries() throws Exception {
        mvc.perform(
                        post("/board")
                                .with(user("boardalice"))
                                .param("title", "제목")
                                .param("body", "내용"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/board")
                                .with(user("boardalice"))
                                .with(csrf())
                                .param("title", " ")
                                .param("body", "내용"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/board")
                                .with(user("boardalice"))
                                .with(csrf())
                                .param("title", "가".repeat(121))
                                .param("body", "내용"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/board")
                                .with(user("boardalice"))
                                .with(csrf())
                                .param("title", "제목")
                                .param("body", "가".repeat(10001)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/board?page=-1").with(user("boardalice")))
                .andExpect(status().isBadRequest());
        assertEquals(0, db.queryForObject("select count(*) from board_post", Integer.class));
    }
}
