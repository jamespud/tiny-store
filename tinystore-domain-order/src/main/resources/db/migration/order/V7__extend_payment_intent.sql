-- 扩展 payment_intent 表，支持命令型事件与支付域协同
-- 新增字段：buyer_id, pay_channel, expire_at, payment_order_id, third_trade_no

ALTER TABLE tinystore_order.payment_intent
    ADD COLUMN IF NOT EXISTS buyer_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pay_channel VARCHAR(32),
    ADD COLUMN IF NOT EXISTS expire_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payment_order_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS third_trade_no VARCHAR(128);

-- 为常用查询字段添加索引
CREATE INDEX IF NOT EXISTS idx_payment_intent_buyer_id ON tinystore_order.payment_intent(buyer_id);
CREATE INDEX IF NOT EXISTS idx_payment_intent_payment_order_id ON tinystore_order.payment_intent(payment_order_id);
CREATE INDEX IF NOT EXISTS idx_payment_intent_third_trade_no ON tinystore_order.payment_intent(third_trade_no);
CREATE INDEX IF NOT EXISTS idx_payment_intent_expire_at ON tinystore_order.payment_intent(expire_at) WHERE status = 'CREATED';

-- 添加注释
COMMENT ON COLUMN tinystore_order.payment_intent.buyer_id IS '买家ID，用于支付域创建支付单';
COMMENT ON COLUMN tinystore_order.payment_intent.pay_channel IS '支付渠道：WECHAT/ALIPAY/UNIONPAY等';
COMMENT ON COLUMN tinystore_order.payment_intent.expire_at IS '支付超时时间，超时后支付域关闭支付单';
COMMENT ON COLUMN tinystore_order.payment_intent.payment_order_id IS '支付域支付单ID，支付域回写，用于关联与对账';
COMMENT ON COLUMN tinystore_order.payment_intent.third_trade_no IS '第三方支付流水号，支付成功后回写，用于幂等与对账';
