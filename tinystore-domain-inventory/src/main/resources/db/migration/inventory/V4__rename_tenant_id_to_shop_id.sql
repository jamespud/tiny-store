-- V4: Rename tenant_id to shop_id for semantic alignment
-- This migration renames tenant_id columns to shop_id across all inventory tables

-- ============================================================
-- inventory_stock table
-- ============================================================
ALTER TABLE tinystore_inventory.inventory_stock RENAME COLUMN tenant_id TO shop_id;

-- Rebuild unique constraint
ALTER TABLE tinystore_inventory.inventory_stock DROP CONSTRAINT IF EXISTS uk_stock_tenant_sku;
ALTER TABLE tinystore_inventory.inventory_stock 
    ADD CONSTRAINT uk_stock_shop_sku UNIQUE (shop_id, sku_id);

-- Rebuild index
DROP INDEX IF EXISTS tinystore_inventory.idx_stock_tenant_sku;
CREATE INDEX idx_stock_shop_sku 
    ON tinystore_inventory.inventory_stock (shop_id, sku_id);

-- Update column comment
COMMENT ON COLUMN tinystore_inventory.inventory_stock.shop_id IS 'Shop identifier - must be included in all WHERE clauses';


-- ============================================================
-- inventory_reservation table
-- ============================================================
ALTER TABLE tinystore_inventory.inventory_reservation RENAME COLUMN tenant_id TO shop_id;

-- Rebuild index
DROP INDEX IF EXISTS tinystore_inventory.idx_reservation_tenant_sku;
CREATE INDEX idx_reservation_shop_sku 
    ON tinystore_inventory.inventory_reservation (shop_id, sku_id);

-- Update column comment
COMMENT ON COLUMN tinystore_inventory.inventory_reservation.shop_id IS 'Shop identifier - must be included in all WHERE clauses';


-- ============================================================
-- inventory_adjustment table
-- ============================================================
ALTER TABLE tinystore_inventory.inventory_adjustment RENAME COLUMN tenant_id TO shop_id;

-- Rebuild index
DROP INDEX IF EXISTS tinystore_inventory.idx_adjustment_tenant_sku_created;
CREATE INDEX idx_adjustment_shop_sku_created 
    ON tinystore_inventory.inventory_adjustment (shop_id, sku_id, created_at DESC);

-- Update column comment
COMMENT ON COLUMN tinystore_inventory.inventory_adjustment.shop_id IS 'Shop identifier for multi-tenancy isolation';
