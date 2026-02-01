-- 扩展 payment_order 与 refund_record 表，增加通知状态字段用于补偿任务

-- payment_order 增加通知状态字段
ALTER TABLE tinystore_payment.payment_order
    ADD COLUMN IF NOT EXISTS notified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS notification_status VARCHAR(32) DEFAULT 'PENDING';

-- refund_record 增加通知状态字段
ALTER TABLE tinystore_payment.refund_record
    ADD COLUMN IF NOT EXISTS notified_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS notification_status VARCHAR(32) DEFAULT 'PENDING';

-- 索引：用于补偿任务扫描"已支付但未通知成功"的记录
CREATE INDEX IF NOT EXISTS idx_payment_order_notification_retry 
    ON tinystore_payment.payment_order(status, notification_status) 
    WHERE status = 'PAID' AND notification_status != 'SUCCESS';

CREATE INDEX IF NOT EXISTS idx_refund_record_notification_retry 
    ON tinystore_payment.refund_record(refund_status, notification_status) 
    WHERE refund_status = 'SUCCESS' AND notification_status != 'SUCCESS';

-- 注释
COMMENT ON COLUMN tinystore_payment.payment_order.notified_at IS '通知订单域的时间戳';
COMMENT ON COLUMN tinystore_payment.payment_order.notification_status IS '通知状态：PENDING/SUCCESS/FAILED';
COMMENT ON COLUMN tinystore_payment.refund_record.notified_at IS '通知订单域的时间戳';
COMMENT ON COLUMN tinystore_payment.refund_record.notification_status IS '通知状态：PENDING/SUCCESS/FAILED';
