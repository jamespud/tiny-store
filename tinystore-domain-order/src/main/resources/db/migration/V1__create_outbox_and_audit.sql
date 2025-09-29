-- V1__create_outbox_and_audit.sql
-- 创建 Outbox 事件表和状态审计表

-- 创建 Outbox 事件表
CREATE TABLE order_outbox_event
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    order_no      VARCHAR(64) NOT NULL,
    event_type    VARCHAR(64) NOT NULL,
    event_payload JSONB       NOT NULL,
    trace_id      VARCHAR(128),
    status        VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    retry_count   INTEGER     NOT NULL DEFAULT 0,
    change_id     BIGSERIAL, -- 预留给 CDC
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_outbox_status_created_at ON order_outbox_event (status, created_at);
CREATE INDEX idx_outbox_order_event ON order_outbox_event (order_no, event_type);
CREATE INDEX idx_outbox_change_id ON order_outbox_event (change_id);
-- 预留给 CDC

-- 创建状态审计表
CREATE TABLE order_status_audit
(
    id          UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    order_no    VARCHAR(64) NOT NULL,
    from_status VARCHAR(64),
    to_status   VARCHAR(64) NOT NULL,
    actor_type  VARCHAR(32) NOT NULL, -- USER, MERCHANT, SYSTEM
    actor_id    VARCHAR(64) NOT NULL,
    reason      VARCHAR(255),
    event_id    UUID,
    trace_id    VARCHAR(128),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_audit_order_created ON order_status_audit (order_no, created_at);
CREATE INDEX idx_audit_trace_id ON order_status_audit (trace_id);