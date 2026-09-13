-- Outbox claim 协议：多副本下每个实例先原子认领一批 PENDING 事件再发布，
-- 避免两个副本同时取到同一批行造成重复投递（C3）。
--
-- claimed_by / claimed_at 记录"谁在什么时候认领的"，用于：
--   1. 发布成功后把自己认领的行推进为 PUBLISHED；
--   2. 回收僵尸认领（实例崩溃导致长期停留在 PROCESSING）。

ALTER TABLE tinystore_order.order_outbox
    ADD COLUMN IF NOT EXISTS claimed_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP;

-- 回收扫描：按 claimed_at 找超时的 PROCESSING 行。
CREATE INDEX IF NOT EXISTS idx_order_outbox_processing_claimed_at
    ON tinystore_order.order_outbox (claimed_at)
    WHERE status = 'PROCESSING';
