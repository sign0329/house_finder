BEGIN READ ONLY;
SET LOCAL statement_timeout='30s';
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT count(*) FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!');
EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM trade WHERE (apartment like '%한화포레나인천구월%' escape '!' or district like '%한화포레나인천구월%' escape '!') ORDER BY deal_date DESC,id DESC LIMIT 12 OFFSET 0;
ROLLBACK;
