ALTER TABLE trade ADD COLUMN source_region VARCHAR(5);
UPDATE trade SET source_region='11110' WHERE source='MOLIT' AND region='서울' AND district='종로구';
CREATE INDEX idx_trade_source_region_month ON trade(source, source_region, source_month);
