-- Local experiment only. The index is rolled back after measurement.
BEGIN;
SET LOCAL lock_timeout='10s';
SET LOCAL statement_timeout='60s';
CREATE TEMP TABLE expected_recent AS SELECT id FROM trade ORDER BY deal_date DESC,id DESC LIMIT 12;
CREATE TEMP TABLE expected_deep AS SELECT id FROM trade ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000;
-- before all_recent run 1
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- before all_recent run 2
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- before all_recent run 3
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- before apartment_keyword run 1
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!');
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!') ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- before apartment_keyword run 2
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!');
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!') ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- before apartment_keyword run 3
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!');
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!') ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- before deep_page run 1
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000;
-- before deep_page run 2
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000;
-- before deep_page run 3
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000;
CREATE INDEX idx_trade_recent_date ON trade (deal_date DESC, id DESC);
-- after all_recent run 1
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- after all_recent run 2
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- after all_recent run 3
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- after apartment_keyword run 1
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!');
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!') ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- after apartment_keyword run 2
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!');
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!') ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- after apartment_keyword run 3
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!');
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!') ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
-- after deep_page run 1
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000;
-- after deep_page run 2
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000;
-- after deep_page run 3
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE 1=1;
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE 1=1 ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000;
DO $$ BEGIN
 IF EXISTS ((SELECT id FROM expected_recent EXCEPT SELECT id FROM (SELECT id FROM trade ORDER BY deal_date DESC,id DESC LIMIT 12) t) UNION ALL (SELECT id FROM expected_deep EXCEPT SELECT id FROM (SELECT id FROM trade ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 120000) t)) THEN RAISE EXCEPTION 'Search results changed'; END IF;
END $$;
SELECT json_build_object('index_bytes',pg_relation_size('idx_trade_recent_date'),'rows',(SELECT count(*) FROM trade));
ROLLBACK;
