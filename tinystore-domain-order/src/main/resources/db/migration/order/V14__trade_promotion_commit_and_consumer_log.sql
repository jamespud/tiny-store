-- V14: Add trade.promotion_commit_status column and consumer_event_log table
-- Supports async promotion commit (quote/commit/release) for the order trade chain.
ALTER TABLE tinystore_order.trade ADD COLUMN IF NOT EXISTS promotion_commit_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

CREATE TABLE IF NOT EXISTS tinystore_order.consumer_event_log (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSED',
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_consumer_event_log_event_consumer
    ON tinystore_order.consumer_event_log (event_id, consumer_name);
