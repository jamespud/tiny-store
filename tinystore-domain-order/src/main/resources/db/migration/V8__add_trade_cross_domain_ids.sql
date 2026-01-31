-- V8: Add cross-domain association IDs to trade table
-- Purpose: Enable order domain to reliably track promotion quote/inventory reservation for compensation flows

ALTER TABLE tinystore_order.trade
    ADD COLUMN promotion_quote_id VARCHAR(128),
    ADD COLUMN promotion_input_hash VARCHAR(255),
    ADD COLUMN inventory_reservation_id VARCHAR(128),
    ADD COLUMN coupon_code VARCHAR(128);

COMMENT ON COLUMN tinystore_order.trade.promotion_quote_id IS 'Promotion checkout quote ID for commit/release operations';
COMMENT ON COLUMN tinystore_order.trade.promotion_input_hash IS 'Promotion quote input hash for validation';
COMMENT ON COLUMN tinystore_order.trade.inventory_reservation_id IS 'Inventory pre-occupy reservation ID for release operations';
COMMENT ON COLUMN tinystore_order.trade.coupon_code IS 'Applied coupon code for promotion tracking';
