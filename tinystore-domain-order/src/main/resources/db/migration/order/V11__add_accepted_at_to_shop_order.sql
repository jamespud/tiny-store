-- Add acceptedAt column to shop_order table to match domain model
ALTER TABLE tinystore_order.shop_order 
ADD COLUMN accepted_at TIMESTAMP;

COMMENT ON COLUMN tinystore_order.shop_order.accepted_at IS '商家接单时间（商家确认订单的时间戳）';
