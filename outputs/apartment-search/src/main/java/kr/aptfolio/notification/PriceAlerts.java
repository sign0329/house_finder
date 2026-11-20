package kr.aptfolio.notification;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class PriceAlerts {
    private static final int PAGE_SIZE = 20;
    private final NamedParameterJdbcTemplate db;

    public PriceAlerts(NamedParameterJdbcTemplate db) {
        this.db = db;
    }

    public record Setting(long id, String name, String location, Long targetPrice) {}

    public record Notice(
            long id,
            String name,
            long price,
            BigDecimal area,
            int floor,
            LocalDate dealDate,
            boolean read,
            Long tradeId) {}

    public record Page(List<Notice> items, int number, boolean hasNext, long unread) {}

    @Transactional
    public void configure(String username, long bookmarkId, long price) {
        if (price <= 0 || price > 1_000_000_000L)
            throw new IllegalArgumentException("목표 가격은 1~1,000,000,000만원으로 입력해주세요.");
        // Use the collector's lock so the subscription boundary cannot split a snapshot.
        db.queryForObject(
                "select id from molit_import_state where id=1 for update", Map.of(), Integer.class);
        long latest =
                db.queryForObject("select coalesce(max(id),0) from trade", Map.of(), Long.class);
        requireUpdated(
                db.update(
                        """
update user_bookmark set target_price=:price, alert_after_trade_id=:latest
where id=:id and user_id=(select id from user_account where username=:username)
""",
                        Map.of(
                                "price",
                                price,
                                "latest",
                                latest,
                                "id",
                                bookmarkId,
                                "username",
                                username)));
    }

    @Transactional
    public void disable(String username, long bookmarkId) {
        db.queryForObject(
                "select id from molit_import_state where id=1 for update", Map.of(), Integer.class);
        requireUpdated(
                db.update(
                        """
update user_bookmark set target_price=null
where id=:id and user_id=(select id from user_account where username=:username)
""",
                        Map.of("id", bookmarkId, "username", username)));
    }

    /** Called inside the successful snapshot transaction while the collection lock is held. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void publish(String region, String month, String snapshot) {
        db.update(
                """
                insert into price_notification(bookmark_id,source_key,price,area,floor,deal_date)
                select b.id,t.source_key,t.price,t.area,t.floor,t.deal_date
                from trade t join user_bookmark b on b.region=t.region and b.district=t.district
                    and b.apartment=t.apartment and b.address=t.address
                where t.source='MOLIT' and t.source_region=:region and t.source_month=:month
                    and t.snapshot_id=:snapshot and t.id>b.alert_after_trade_id
                    and t.price<=b.target_price
                    and not exists(select 1 from price_notification n
                        where n.bookmark_id=b.id and n.source_key=t.source_key)
                """,
                Map.of("region", region, "month", month, "snapshot", snapshot));
    }

    @Transactional(readOnly = true)
    public List<Setting> settings(String username) {
        return db.query(
                """
                select b.* from user_bookmark b join user_account u on u.id=b.user_id
                where u.username=:username order by b.id desc limit 200
                """,
                Map.of("username", username),
                (r, n) ->
                        new Setting(
                                r.getLong("id"),
                                r.getString("apartment"),
                                r.getString("region") + " " + r.getString("district"),
                                r.getObject("target_price", Long.class)));
    }

    @Transactional(readOnly = true)
    public Page list(String username, int page) {
        if (page < 0 || page > 10000) throw new IllegalArgumentException("페이지 범위를 확인해주세요.");
        var params =
                Map.<String, Object>of(
                        "username", username, "limit", PAGE_SIZE + 1, "offset", page * PAGE_SIZE);
        var items =
                db.query(
                        """
                        select n.*,b.apartment,t.id as trade_id
                        from price_notification n join user_bookmark b on b.id=n.bookmark_id
                        join user_account u on u.id=b.user_id
                        left join trade t on t.source_key=n.source_key and t.source='MOLIT'
                        where u.username=:username order by n.id desc limit :limit offset :offset
                        """,
                        params,
                        (r, n) ->
                                new Notice(
                                        r.getLong("id"),
                                        r.getString("apartment"),
                                        r.getLong("price"),
                                        r.getBigDecimal("area"),
                                        r.getInt("floor"),
                                        r.getDate("deal_date").toLocalDate(),
                                        r.getBoolean("is_read"),
                                        r.getObject("trade_id", Long.class)));
        long unread =
                db.queryForObject(
                        """
select count(*) from price_notification n join user_bookmark b on b.id=n.bookmark_id
join user_account u on u.id=b.user_id where u.username=:username and n.is_read=false
""",
                        params,
                        Long.class);
        return new Page(
                List.copyOf(items.subList(0, Math.min(items.size(), PAGE_SIZE))),
                page,
                items.size() > PAGE_SIZE,
                unread);
    }

    @Transactional
    public void markRead(String username, long id) {
        requireUpdated(
                db.update(
                        """
update price_notification set is_read=true where id=:id and bookmark_id in
  (select b.id from user_bookmark b join user_account u on u.id=b.user_id where u.username=:username)
""",
                        Map.of("id", id, "username", username)));
    }

    private static void requireUpdated(int count) {
        if (count == 0) throw new NoSuchElementException();
    }
}
