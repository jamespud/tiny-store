-- V16: outbox 待发布查询索引（getPendingEvents 每轮全表排序——压测时成为发布瓶颈）
CREATE INDEX IF NOT EXISTS idx_order_outbox_status_created
    ON tinystore_order.order_outbox (status, created_at);

-- PendingCommitTimeoutScheduler 扫描索引（PENDING + UNPAID + createdAt 阈值）
CREATE INDEX IF NOT EXISTS idx_trade_pending_commit_scan
    ON tinystore_order.trade (promotion_commit_status, pay_status, created_at);
