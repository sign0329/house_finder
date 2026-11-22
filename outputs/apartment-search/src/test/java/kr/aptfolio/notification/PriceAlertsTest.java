package kr.aptfolio.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import kr.aptfolio.bookmark.Bookmarks;
import kr.aptfolio.collection.molit.MolitStore;
import kr.aptfolio.collection.molit.MolitXml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:price-alerts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class PriceAlertsTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired Bookmarks bookmarks;
    @Autowired MolitStore store;
    static final YearMonth MONTH = YearMonth.of(2026, 9);
    long bookmark;

    MolitXml.Item item(int floor, long price) {
        return new MolitXml.Item(
                "알림 아파트",
                "서울 종로구 테스트 1",
                new BigDecimal("84"),
                floor,
                2010,
                MONTH.atDay(1),
                price,
                false);
    }

    @BeforeEach
    void prepare() {
        db.update("delete from user_account");
        db.update("delete from trade");
        db.update(
                "insert into user_account(username,nickname,password_hash)"
                        + " values('alice','앨리스','fixture'),('bob','밥','fixture')");
        store.replace(MONTH, List.of(item(1, 40000)));
        bookmark = bookmarks.save("alice", db.queryForObject("select id from trade", Long.class));
    }

    void configure() throws Exception {
        mvc.perform(
                        post("/account/alerts/" + bookmark + "/target")
                                .with(user("alice"))
                                .with(csrf())
                                .param("price", "50000"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void configuringTargetSucceeds() throws Exception {
        configure();
    }

    @Test
    void onlyNewMatchingTradesNotifyAndRepeatedCollectionDoesNotDuplicate() throws Exception {
        configure();
        var trades = List.of(item(1, 40000), item(2, 50000), item(3, 50001));
        store.replace(MONTH, trades);
        store.replace(MONTH, trades);
        assertEquals(
                1, db.queryForObject("select count(*) from price_notification", Integer.class));
        mvc.perform(get("/account/alerts").with(user("alice"))).andExpect(status().isOk());
        store.replace(MONTH, List.of(item(1, 40000))); // cancellation/removal then reappearance
        store.replace(MONTH, trades);
        assertEquals(
                1, db.queryForObject("select count(*) from price_notification", Integer.class));
    }

    @Test
    void ownershipCsrfAndInvalidPricesAreEnforced() throws Exception {
        mvc.perform(get("/account/alerts")).andExpect(status().is3xxRedirection());
        mvc.perform(
                        post("/account/alerts/" + bookmark + "/target")
                                .with(user("alice"))
                                .param("price", "50000"))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/account/alerts/" + bookmark + "/target")
                                .with(user("bob"))
                                .with(csrf())
                                .param("price", "50000"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/account/alerts/" + bookmark + "/target")
                                .with(user("alice"))
                                .with(csrf())
                                .param("price", "0"))
                .andExpect(status().isBadRequest());
        configure();
        store.replace(MONTH, List.of(item(1, 40000), item(2, 45000)));
        long id = db.queryForObject("select id from price_notification", Long.class);
        mvc.perform(post("/account/alerts/read/" + id).with(user("bob")).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/account/alerts/read/" + id).with(user("alice")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(db.queryForObject("select is_read from price_notification", Boolean.class));
    }

    @Test
    void failedImportRollsBackTradesAndNotifications() throws Exception {
        configure();
        assertThrows(
                Exception.class, () -> store.replace(MONTH, List.of(item(2, 45000), item(3, -1))));
        assertEquals(
                0, db.queryForObject("select count(*) from price_notification", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from trade", Integer.class));
    }

    @Test
    void disabledAndDeletedBookmarksStopNotifications() throws Exception {
        configure();
        mvc.perform(
                        post("/account/alerts/" + bookmark + "/disable")
                                .with(user("alice"))
                                .with(csrf()))
                .andExpect(status().is3xxRedirection());
        store.replace(MONTH, List.of(item(1, 40000), item(2, 45000)));
        assertEquals(
                0, db.queryForObject("select count(*) from price_notification", Integer.class));
        configure();
        store.replace(MONTH, List.of(item(1, 40000), item(2, 45000), item(3, 45000)));
        bookmarks.remove("alice", bookmark);
        assertEquals(
                0, db.queryForObject("select count(*) from price_notification", Integer.class));
    }
}
