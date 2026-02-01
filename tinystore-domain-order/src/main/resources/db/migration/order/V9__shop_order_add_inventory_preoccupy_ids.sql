-- V9: Add inventory_pre_occupy_ids_json to shop_order table
-- Purpose: Store inventory reservation IDs (preOccupyIds list) per shop for commit/release operations

ALTER TABLE tinystore_order.shop_order
    ADD COLUMN inventory_pre_occupy_ids_json TEXT;

COMMENT ON COLUMN tinystore_order.shop_order.inventory_pre_occupy_ids_json IS 'JSON array of inventory pre-occupy IDs (from StockPreOccupyResponse.preOccupyIds)';
