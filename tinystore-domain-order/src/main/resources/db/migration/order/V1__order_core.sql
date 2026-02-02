-- V1__order_core.sql
-- 订单核心表结构

CREATE SCHEMA IF NOT EXISTS tinystore_order;

-- 交易主单表
CREATE TABLE IF NOT EXISTS tinystore_order.trade (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL UNIQUE,
    buyer_id VARCHAR(64) NOT NULL,
    buyer_nick VARCHAR(255),
    pay_status VARCHAR(32) NOT NULL DEFAULT 'UNPAID',
    total_amount_cents BIGINT NOT NULL,
    discount_amount_cents BIGINT DEFAULT 0,
    payable_amount_cents BIGINT NOT NULL,
    pay_type VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trade_buyer_id ON tinystore_order.trade(buyer_id);
CREATE INDEX IF NOT EXISTS idx_trade_pay_status ON tinystore_order.trade(pay_status);
CREATE INDEX IF NOT EXISTS idx_trade_created_at ON tinystore_order.trade(created_at DESC);

-- 店铺子单表
CREATE TABLE IF NOT EXISTS tinystore_order.shop_order (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    trade_id VARCHAR(64) NOT NULL,
    seller_id VARCHAR(64) NOT NULL,
    shop_id VARCHAR(64) NOT NULL,
    order_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_PAY',
    inventory_status VARCHAR(32) NOT NULL DEFAULT 'UNLOCKED',
    promotion_status VARCHAR(32) NOT NULL DEFAULT 'UNAPPLIED',
    logistics_status VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shop_order_trade_id ON tinystore_order.shop_order(trade_id);
CREATE INDEX IF NOT EXISTS idx_shop_order_seller_id ON tinystore_order.shop_order(seller_id);
CREATE INDEX IF NOT EXISTS idx_shop_order_order_status ON tinystore_order.shop_order(order_status);
CREATE INDEX IF NOT EXISTS idx_shop_order_created_at ON tinystore_order.shop_order(created_at DESC);

-- 订单行表
CREATE TABLE IF NOT EXISTS tinystore_order.order_line (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    product_name VARCHAR(255),
    quantity INTEGER NOT NULL,
    price_cents BIGINT NOT NULL,
    line_amount_cents BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_line_order_id ON tinystore_order.order_line(order_id);

-- 支付意图表（为实现支付闭环）
CREATE TABLE IF NOT EXISTS tinystore_order.payment_intent (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(64) NOT NULL UNIQUE,
    trade_id VARCHAR(64) NOT NULL,
    amount_cents BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_intent_trade_id ON tinystore_order.payment_intent(trade_id);
CREATE INDEX IF NOT EXISTS idx_payment_intent_status ON tinystore_order.payment_intent(status);

COMMENT ON TABLE tinystore_order.trade IS '交易主单表';
COMMENT ON TABLE tinystore_order.shop_order IS '店铺子单表';
COMMENT ON TABLE tinystore_order.order_line IS '订单行表';
COMMENT ON TABLE tinystore_order.payment_intent IS '支付意图表';
