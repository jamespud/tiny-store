-- P0-2 (review round 2): the success record for create-trade idempotency must be durable and atomic with
-- the trade itself. Before this, the only success record was in Redis, written in afterCommit(): a Redis
-- failure there turned an already-committed trade into an HTTP error (ambiguous commit), and a Redis restart
-- allowed the same Idempotency-Key to create a second trade.
--
-- Redis stays as the fast-path concurrency guard (PROCESSING/IN_PROGRESS); this table is the authority for
-- "this key already produced this exact response".
CREATE TABLE IF NOT EXISTS tinystore_order.trade_idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    scope VARCHAR(64) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    state VARCHAR(20) NOT NULL,
    trade_id VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trade_idempotency_created_at
    ON tinystore_order.trade_idempotency_record (created_at);
