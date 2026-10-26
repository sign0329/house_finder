ALTER TABLE trade ADD COLUMN source_key VARCHAR(80);
ALTER TABLE trade ADD COLUMN source_month VARCHAR(6);
ALTER TABLE trade ADD COLUMN snapshot_id VARCHAR(36);
CREATE UNIQUE INDEX idx_trade_source_key ON trade(source_key);
CREATE TABLE molit_import_state (
 id INTEGER PRIMARY KEY,
 last_month VARCHAR(6),
 last_count INTEGER,
 updated_at TIMESTAMP
);
INSERT INTO molit_import_state(id) VALUES(1);
