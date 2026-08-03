-- V15: backfill promotion_commit_status + optimistic lock version column
-- 历史订单（flag 开启前）用同步 commit 创建、券已实际预占 → COMMITTED 是真实语义；
-- 已关闭订单保持 PENDING（scheduler 双检查会跳过，无影响）。
ALTER TABLE tinystore_order.trade ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

UPDATE tinystore_order.trade
SET promotion_commit_status = 'COMMITTED'
WHERE promotion_commit_status = 'PENDING' AND closed_at IS NULL;
