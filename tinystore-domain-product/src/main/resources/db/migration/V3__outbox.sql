-- V3__outbox.sql
-- Outbox pattern table for reliable event publishing

CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(200) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    tenant_id VARCHAR(100) NOT NULL
);

-- Indexes for outbox polling and cleanup
CREATE INDEX idx_outbox_status_occurred ON outbox(status, occurred_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_published ON outbox(published_at) WHERE published_at IS NOT NULL;
CREATE INDEX idx_outbox_tenant ON outbox(tenant_id);

-- Comments
COMMENT ON TABLE outbox IS 'Transactional outbox for reliable event publishing to Kafka';
COMMENT ON COLUMN outbox.aggregate_type IS 'Type of aggregate (Product, Sku, PricingRule)';
COMMENT ON COLUMN outbox.aggregate_id IS 'Business identifier of the aggregate';
COMMENT ON COLUMN outbox.event_type IS 'Fully qualified event type name';
COMMENT ON COLUMN outbox.payload IS 'Event payload serialized as JSONB';
COMMENT ON COLUMN outbox.status IS 'Publishing status: PENDING, PUBLISHED, FAILED';
COMMENT ON COLUMN outbox.retry_count IS 'Number of publishing retry attempts';
