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
                "spring.datasource.url=jdbc:h2:mem:board-search;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Transactional
class BoardSearchTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired BoardPosts posts;

    @BeforeEach
    void setup() {
        db.update(
                "insert into user_account(username,nickname,password_hash)"
                        + " values('searcher','회원','unused')");
    }

    long create(String category, String title, String body) throws Exception {
        var r =
                mvc.perform(
                                post("/board")
                                        .with(user("searcher"))
                                        .with(csrf())
                                        .param("category", category)
                                        .param("title", title)
                                        .param("body", body))
                        .andExpect(status().is3xxRedirection())
                        .andReturn();
        return Long.parseLong(r.getResponse().getRedirectedUrl().replace("/board/", ""));
    }

    @Test
    void categoryIsSavedEditedAndValidated() throws Exception {
        long id = create("QUESTION", "질문 제목", "내용");
        assertEquals(
                "QUESTION",
                db.queryForObject("select category from board_post where id=?", String.class, id));
        mvc.perform(
                        post("/board/" + id)
                                .with(user("searcher"))
                                .with(csrf())
                                .param("category", "INFO")
                                .param("title", "수정")
                                .param("body", "내용"))
                .andExpect(status().is3xxRedirection());
        assertEquals(
                "INFO",
                db.queryForObject("select category from board_post where id=?", String.class, id));
        mvc.perform(
                        post("/board")
                                .with(user("searcher"))
                                .with(csrf())
                                .param("category", "BAD")
                                .param("title", "제목")
                                .param("body", "내용"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchCombinesCategoryAndTitleBodyAndExcludesHidden() throws Exception {
        create("QUESTION", "역세권 질문", "내용");
        create("INFO", "교통 정보", "역세권");
        create("FREE", "무관한 제목", "본문");
        long hidden = create("QUESTION", "숨김 역세권", "본문");
        db.update("update board_post set hidden=true where id=?", hidden);
        mvc.perform(
                        get("/board")
                                .with(user("searcher"))
                                .param("q", "역세권")
                                .param("category", "QUESTION"))
                .andExpect(content().string(containsString("역세권 질문")))
                .andExpect(content().string(not(containsString("교통 정보"))))
                .andExpect(content().string(not(containsString("숨김 역세권"))));
        mvc.perform(get("/board").with(user("searcher")).param("q", "역세권").param("scope", "BODY"))
                .andExpect(content().string(containsString("교통 정보")))
                .andExpect(content().string(not(containsString("역세권 질문"))));
    }

    @Test
    void wildcardsAreLiteralAndInvalidFiltersRejected() throws Exception {
        create("FREE", "100%_! 실제", "본문");
        create("FREE", "관련없는글", "본문");
        mvc.perform(get("/board").with(user("searcher")).param("q", "%_!"))
                .andExpect(content().string(containsString("100%_! 실제")))
                .andExpect(content().string(not(containsString("관련없는글"))));
        for (String key : new String[] {"category", "scope"})
            mvc.perform(get("/board").with(user("searcher")).param(key, "INVALID"))
                    .andExpect(status().isBadRequest());
        mvc.perform(get("/board").with(user("searcher")).param("q", "가".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginationRetainsFiltersAndLegacyPostsDefaultToFree() throws Exception {
        long legacy = posts.create("searcher", "기존", "내용");
        assertEquals(
                "FREE",
                db.queryForObject(
                        "select category from board_post where id=?", String.class, legacy));
        for (int i = 0; i < 21; i++) create("INFO", "교통 " + i, "내용");
        mvc.perform(
                        get("/board")
                                .with(user("searcher"))
                                .param("q", "교통")
                                .param("scope", "TITLE")
                                .param("category", "INFO"))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(containsString("category=INFO")))
                .andExpect(content().string(containsString("scope=TITLE")));
    }
}
