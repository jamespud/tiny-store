-- 传输类失败退避重试：Kafka 短暂不可用不应让事件进入永久 FAILED（C12）。
--
-- next_attempt_at 为 NULL 表示立即可认领；否则只有到达该时间后才重新可认领。
-- 只有"不可恢复的载荷/校验失败"才会直接把事件置为 FAILED。

ALTER TABLE tinystore_order.order_outbox
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_order_outbox_pending_next_attempt
    ON tinystore_order.order_outbox (next_attempt_at)
    WHERE status = 'PENDING';
