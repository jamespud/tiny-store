-- Phase 2: Rename orderNo/order_no to tradeId/trade_id across promotion domain
-- This migration ensures cross-domain contract alignment with order domain

-- 1. Rename checkout_quote.order_no to trade_id
ALTER TABLE promotion.checkout_quote RENAME COLUMN order_no TO trade_id;

-- 2. Rename corresponding index
DROP INDEX IF EXISTS promotion.idx_checkout_quote_order_no;
CREATE INDEX IF NOT EXISTS idx_checkout_quote_trade_id ON promotion.checkout_quote (trade_id);

-- 3. Rename user_coupon.used_order_no to used_trade_id
ALTER TABLE promotion.user_coupon RENAME COLUMN used_order_no TO used_trade_id;

-- Add comments for clarity
COMMENT ON COLUMN promotion.checkout_quote.trade_id IS '关联的交易ID，对应订单域的 tradeId';
COMMENT ON COLUMN promotion.user_coupon.used_trade_id IS '优惠券使用时关联的交易ID';
