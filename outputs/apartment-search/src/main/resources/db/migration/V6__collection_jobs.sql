CREATE TABLE collection_job (
 id VARCHAR(36) PRIMARY KEY,
 region_code VARCHAR(5) NOT NULL,
 province VARCHAR(30) NOT NULL,
 district VARCHAR(50) NOT NULL,
 deal_month VARCHAR(6) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('RUNNING','SUCCEEDED','FAILED','INTERRUPTED')),
 attempt INTEGER NOT NULL,
 saved INTEGER NOT NULL DEFAULT 0,
 cancelled INTEGER NOT NULL DEFAULT 0,
 failure_type VARCHAR(32),
 started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at TIMESTAMP,
 lease_until TIMESTAMP NOT NULL
);
CREATE INDEX idx_collection_segment ON collection_job(region_code,deal_month,started_at);
CREATE INDEX idx_collection_status ON collection_job(status,lease_until);
