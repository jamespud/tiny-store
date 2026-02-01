-- 初始化支付域 schema 与核心表
-- Schema: tinystore_payment

-- 创建 schema
CREATE SCHEMA IF NOT EXISTS tinystore_payment;

-- 支付单表（PaymentOrder）
CREATE TABLE IF NOT EXISTS tinystore_payment.payment_order (
    id BIGSERIAL PRIMARY KEY,
    payment_order_id VARCHAR(64) NOT NULL UNIQUE,
    payment_intent_id VARCHAR(64) NOT NULL UNIQUE,
    trade_id VARCHAR(64) NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    amount_cents BIGINT NOT NULL,
    pay_channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    third_trade_no VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    closed_at TIMESTAMP,
    expire_at TIMESTAMP,
    CONSTRAINT chk_amount_positive CHECK (amount_cents > 0)
);

-- 退款记录表（RefundRecord）
CREATE TABLE IF NOT EXISTS tinystore_payment.refund_record (
    id BIGSERIAL PRIMARY KEY,
    refund_id VARCHAR(64) NOT NULL UNIQUE,
    payment_intent_id VARCHAR(64) NOT NULL,
    payment_order_id VARCHAR(64) NOT NULL,
    trade_id VARCHAR(64) NOT NULL,
    refund_amount_cents BIGINT NOT NULL,
    refund_status VARCHAR(32) NOT NULL,
    third_refund_no VARCHAR(128),
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    refunded_at TIMESTAMP,
    CONSTRAINT chk_refund_amount_positive CHECK (refund_amount_cents > 0)
);

-- 索引
CREATE INDEX idx_payment_order_payment_intent_id ON tinystore_payment.payment_order(payment_intent_id);
CREATE INDEX idx_payment_order_trade_id ON tinystore_payment.payment_order(trade_id);
CREATE INDEX idx_payment_order_buyer_id ON tinystore_payment.payment_order(buyer_id);
CREATE INDEX idx_payment_order_status ON tinystore_payment.payment_order(status);
CREATE INDEX idx_payment_order_expire_at ON tinystore_payment.payment_order(expire_at) WHERE status = 'UNPAID';
CREATE INDEX idx_payment_order_third_trade_no ON tinystore_payment.payment_order(third_trade_no);

CREATE INDEX idx_refund_record_refund_id ON tinystore_payment.refund_record(refund_id);
CREATE INDEX idx_refund_record_payment_order_id ON tinystore_payment.refund_record(payment_order_id);
CREATE INDEX idx_refund_record_trade_id ON tinystore_payment.refund_record(trade_id);
CREATE INDEX idx_refund_record_status ON tinystore_payment.refund_record(refund_status);

-- 注释
COMMENT ON TABLE tinystore_payment.payment_order IS '支付单表，支付域聚合根';
COMMENT ON TABLE tinystore_payment.refund_record IS '退款记录表';

COMMENT ON COLUMN tinystore_payment.payment_order.payment_order_id IS '支付单ID，支付域内部唯一标识';
COMMENT ON COLUMN tinystore_payment.payment_order.payment_intent_id IS '支付意图ID，订单域传入，幂等主键';
COMMENT ON COLUMN tinystore_payment.payment_order.trade_id IS '交易ID，关联订单域';
COMMENT ON COLUMN tinystore_payment.payment_order.buyer_id IS '买家ID';
COMMENT ON COLUMN tinystore_payment.payment_order.amount_cents IS '支付金额（分）';
COMMENT ON COLUMN tinystore_payment.payment_order.pay_channel IS '支付渠道：WECHAT/ALIPAY/UNIONPAY/DEFAULT';
COMMENT ON COLUMN tinystore_payment.payment_order.status IS '支付状态：UNPAID/PAID/CLOSED/EXPIRED/FAILED';
COMMENT ON COLUMN tinystore_payment.payment_order.third_trade_no IS '第三方支付流水号';
COMMENT ON COLUMN tinystore_payment.payment_order.expire_at IS '支付超时时间';

COMMENT ON COLUMN tinystore_payment.refund_record.refund_id IS '退款单号，订单域传入，幂等主键';
COMMENT ON COLUMN tinystore_payment.refund_record.payment_order_id IS '关联支付单ID';
COMMENT ON COLUMN tinystore_payment.refund_record.refund_status IS '退款状态：REQUESTED/PROCESSING/SUCCESS/FAIL';
COMMENT ON COLUMN tinystore_payment.refund_record.third_refund_no IS '第三方退款流水号';
