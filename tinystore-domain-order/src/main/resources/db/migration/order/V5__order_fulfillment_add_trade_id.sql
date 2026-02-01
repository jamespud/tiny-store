-- V5__order_fulfillment_add_trade_id.sql
-- 为履约包裹表添加 trade_id 字段以支持按交易查询包裹

ALTER TABLE tinystore_order.fulfillment_package 
ADD COLUMN trade_id VARCHAR(64);

CREATE INDEX idx_fulfillment_package_trade_id ON tinystore_order.fulfillment_package(trade_id);

COMMENT ON COLUMN tinystore_order.fulfillment_package.trade_id IS '关联的交易ID';
