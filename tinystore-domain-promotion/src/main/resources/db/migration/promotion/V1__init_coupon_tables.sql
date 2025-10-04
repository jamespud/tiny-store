CREATE SCHEMA IF NOT EXISTS promotion;

CREATE TABLE IF NOT EXISTS promotion.coupon (
    id UUID PRIMARY KEY,
    coupon_no VARCHAR(64) NOT NULL UNIQUE,
    coupon_type VARCHAR(32) NOT NULL,
    shop_id VARCHAR(64),
    threshold_amount NUMERIC(12, 2),
    discount_amount NUMERIC(12, 2),
    discount_rate NUMERIC(5, 2),
    max_discount_amount NUMERIC(12, 2),
    total_stock BIGINT NOT NULL,
    used_stock BIGINT NOT NULL DEFAULT 0,
    start_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    end_time TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    mutex_group VARCHAR(64),
    priority INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    budget_type VARCHAR(32),
    budget_total NUMERIC(16, 2),
    budget_used NUMERIC(16, 2) DEFAULT 0,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_coupon_status_time
    ON promotion.coupon (status, start_time, end_time);

CREATE INDEX IF NOT EXISTS idx_coupon_shop_status
    ON promotion.coupon (shop_id, status);

CREATE TABLE IF NOT EXISTS promotion.user_coupon (
    id UUID PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    coupon_id UUID NOT NULL REFERENCES promotion.coupon(id),
    coupon_no VARCHAR(64) NOT NULL,
    receive_time TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    use_status VARCHAR(32) NOT NULL,
    lock_id VARCHAR(64),
    lock_expire_time TIMESTAMP WITHOUT TIME ZONE,
    used_order_no VARCHAR(64),
    used_time TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_coupon_user_status
    ON promotion.user_coupon (user_id, use_status);

CREATE INDEX IF NOT EXISTS idx_user_coupon_coupon_status
    ON promotion.user_coupon (coupon_id, use_status);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_coupon_lock
    ON promotion.user_coupon (lock_id)
    WHERE lock_id IS NOT NULL;
