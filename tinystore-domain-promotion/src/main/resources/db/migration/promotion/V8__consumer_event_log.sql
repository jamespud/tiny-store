CREATE TABLE IF NOT EXISTS promotion.consumer_event_log (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSED',
    processed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_consumer_event_log_event_consumer
    ON promotion.consumer_event_log (event_id, consumer_name);

CREATE INDEX IF NOT EXISTS idx_consumer_event_log_processed_at
    ON promotion.consumer_event_log (processed_at);
