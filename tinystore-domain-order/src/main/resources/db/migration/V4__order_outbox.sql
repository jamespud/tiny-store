-- V4__order_outbox.sql
-- Outbox 事件投递表

CREATE TABLE IF NOT EXISTS tinystore_order.order_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(128) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_order_outbox_status_created ON tinystore_order.order_outbox(status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_order_outbox_event_type ON tinystore_order.order_outbox(event_type);
CREATE INDEX idx_order_outbox_aggregate_id ON tinystore_order.order_outbox(aggregate_id);

COMMENT ON TABLE tinystore_order.order_outbox IS 'Outbox 事件投递表';
