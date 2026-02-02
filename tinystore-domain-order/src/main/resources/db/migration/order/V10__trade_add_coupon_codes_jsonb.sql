-- V10: Add coupon_codes JSONB array to support multi-coupon tracking
-- Purpose: Enable trade to store multiple coupon codes (couponNo) for both platform and shop coupons

ALTER TABLE tinystore_order.trade
    ADD COLUMN coupon_codes JSONB;

-- Default to empty array for new rows (optional, can be handled at application layer)
-- ALTER TABLE tinystore_order.trade ALTER COLUMN coupon_codes SET DEFAULT '[]'::jsonb;

COMMENT ON COLUMN tinystore_order.trade.coupon_codes IS 'Array of applied coupon codes (couponNo format) in JSONB, e.g., ["C202602","P8888"]';
