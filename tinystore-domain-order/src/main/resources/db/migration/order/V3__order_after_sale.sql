-- V3__order_after_sale.sql
-- 售后表结构

CREATE TABLE IF NOT EXISTS tinystore_order.after_sale_case (
    id BIGSERIAL PRIMARY KEY,
    case_id VARCHAR(64) NOT NULL UNIQUE,
    trade_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    seller_id VARCHAR(64) NOT NULL,
    case_type VARCHAR(32) NOT NULL,
    case_status VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    refund_amount_cents BIGINT,
    refund_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_after_sale_case_trade_id ON tinystore_order.after_sale_case(trade_id);
CREATE INDEX IF NOT EXISTS idx_after_sale_case_order_id ON tinystore_order.after_sale_case(order_id);
CREATE INDEX IF NOT EXISTS idx_after_sale_case_buyer_id ON tinystore_order.after_sale_case(buyer_id);
CREATE INDEX IF NOT EXISTS idx_after_sale_case_case_status ON tinystore_order.after_sale_case(case_status);
CREATE INDEX IF NOT EXISTS idx_after_sale_case_created_at ON tinystore_order.after_sale_case(created_at DESC);

-- 退款 ID 唯一性约束（幂等）
CREATE UNIQUE INDEX IF NOT EXISTS idx_after_sale_case_refund_id ON tinystore_order.after_sale_case(refund_id) WHERE refund_id IS NOT NULL;

COMMENT ON TABLE tinystore_order.after_sale_case IS '售后单表';
