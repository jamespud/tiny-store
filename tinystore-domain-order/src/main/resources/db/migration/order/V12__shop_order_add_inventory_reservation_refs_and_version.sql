-- V12: Add inventory reservation refs and projection version to shop_order
-- New columns support canonical reservation references (version 2 orders)
-- Legacy column inventory_pre_occupy_ids_json is preserved for version 1 orders

-- Step 1: Add canonical inventory reservation refs JSON column
ALTER TABLE tinystore_order.shop_order
    ADD COLUMN IF NOT EXISTS inventory_reservation_refs_json TEXT;

-- Step 2: Add inventory projection version (1=legacy V2 deduct, 2=canonical reservation)
ALTER TABLE tinystore_order.shop_order
    ADD COLUMN IF NOT EXISTS inventory_projection_version INT NOT NULL DEFAULT 1;

-- Step 3: Index for querying version 2 orders during drain phase
CREATE INDEX IF NOT EXISTS idx_shop_order_projection_version
    ON tinystore_order.shop_order (inventory_projection_version)
    WHERE inventory_projection_version = 2;
