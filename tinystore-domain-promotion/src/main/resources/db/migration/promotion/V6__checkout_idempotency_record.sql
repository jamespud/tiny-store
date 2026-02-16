CREATE TABLE IF NOT EXISTS promotion.idempotency_record (
    id UUID PRIMARY KEY,
    op VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json JSONB NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_record_op_key
    ON promotion.idempotency_record (op, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_idempotency_record_expires_at
    ON promotion.idempotency_record (expires_at);
