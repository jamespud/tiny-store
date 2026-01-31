-- Phase 2: Rename orderNo/order_no to tradeId/trade_id in inventory domain
-- This migration ensures cross-domain contract alignment with order domain

-- 1. Rename inventory_reservation.order_no to trade_id
ALTER TABLE tinystore_inventory.inventory_reservation RENAME COLUMN order_no TO trade_id;

-- 2. Rename corresponding index
DROP INDEX IF EXISTS tinystore_inventory.idx_reservation_order_no;
CREATE INDEX IF NOT EXISTS idx_reservation_trade_id ON tinystore_inventory.inventory_reservation (trade_id);

-- Add comment for clarity
COMMENT ON COLUMN tinystore_inventory.inventory_reservation.trade_id IS '关联的交易ID，对应订单域的 tradeId';
