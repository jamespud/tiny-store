-- V2__create_order_idempotency.sql
-- 创建幂等性控制表

CREATE TABLE order_idempotency
(
    request_id    VARCHAR(128) PRIMARY KEY,
    order_no      VARCHAR(64),
    operation     VARCHAR(64) NOT NULL,
    response_data JSONB,
    status        VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    trace_id      VARCHAR(128),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours')
);

-- 创建索引
CREATE INDEX idx_idempotency_expires ON order_idempotency (expires_at);
CREATE INDEX idx_idempotency_order_op ON order_idempotency (order_no, operation);