CREATE TABLE IF NOT EXISTS promotion.checkout_quote (
    id UUID PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    pricing_rules_version VARCHAR(64),
    shipping_rules_version VARCHAR(64),
    snapshot JSONB NOT NULL,
    expires_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    order_no VARCHAR(64),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_checkout_quote_user_status_expires
    ON promotion.checkout_quote (user_id, status, expires_at);

CREATE INDEX IF NOT EXISTS idx_checkout_quote_order_no
    ON promotion.checkout_quote (order_no);

CREATE TABLE IF NOT EXISTS promotion.campaign_full_reduction (
    id UUID PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    ladder JSONB NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_campaign_full_reduction_status_time
    ON promotion.campaign_full_reduction (status, start_time, end_time);

CREATE TABLE IF NOT EXISTS promotion.seckill_price_rule (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    scope JSONB,
    content JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_seckill_price_rule_status_time
    ON promotion.seckill_price_rule (status, start_time, end_time);

CREATE TABLE IF NOT EXISTS promotion.shipping_rule (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    scope JSONB,
    content JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shipping_rule_status
    ON promotion.shipping_rule (status);

