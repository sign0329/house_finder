-- Nationwide recent-order searches have no leading region/apartment predicate.
-- Build during a maintenance window: CREATE INDEX blocks writes until commit.
CREATE INDEX idx_trade_recent_date ON trade (deal_date DESC, id DESC);
