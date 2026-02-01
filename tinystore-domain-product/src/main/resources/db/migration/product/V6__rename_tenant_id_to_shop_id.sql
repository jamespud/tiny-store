-- V6: Rename tenant_id to shop_id for semantic alignment
-- This migration renames tenant_id columns to shop_id across all product tables

-- ============================================================
-- product table
-- ============================================================
ALTER TABLE product RENAME COLUMN tenant_id TO shop_id;

-- Rebuild indexes
DROP INDEX IF EXISTS idx_product_tenant_category_status;
CREATE INDEX idx_product_shop_category_status ON product(shop_id, category_id, status);

DROP INDEX IF EXISTS idx_product_tenant_status;
CREATE INDEX idx_product_shop_status ON product(shop_id, status);

-- Rebuild unique index
DROP INDEX IF EXISTS uk_product_tenant_product_id;
CREATE UNIQUE INDEX uk_product_shop_product_id ON product(shop_id, product_id);

-- Update column comment
COMMENT ON COLUMN product.shop_id IS 'Shop identifier - must be included in all WHERE clauses';


-- ============================================================
-- sku table
-- ============================================================
ALTER TABLE sku RENAME COLUMN tenant_id TO shop_id;

-- Rebuild indexes
DROP INDEX IF EXISTS idx_sku_tenant_product;
CREATE INDEX idx_sku_shop_product ON sku(shop_id, product_id);

-- Rebuild unique index
DROP INDEX IF EXISTS uk_sku_tenant_sku_id;
CREATE UNIQUE INDEX uk_sku_shop_sku_id ON sku(shop_id, sku_id);

-- Update column comment
COMMENT ON COLUMN sku.shop_id IS 'Shop identifier - must be included in all WHERE clauses';


-- ============================================================
-- product_category table
-- ============================================================
ALTER TABLE product_category RENAME COLUMN tenant_id TO shop_id;

-- Rebuild index
DROP INDEX IF EXISTS idx_product_category_tenant;
CREATE INDEX idx_product_category_shop ON product_category(shop_id);

-- Update column comment
COMMENT ON COLUMN product_category.shop_id IS 'Shop identifier for multi-tenancy isolation';


-- ============================================================
-- product_tag table
-- ============================================================
ALTER TABLE product_tag RENAME COLUMN tenant_id TO shop_id;

-- Rebuild index
DROP INDEX IF EXISTS idx_product_tag_tenant;
CREATE INDEX idx_product_tag_shop ON product_tag(shop_id);

-- Update column comment
COMMENT ON COLUMN product_tag.shop_id IS 'Shop identifier for multi-tenancy isolation';


-- ============================================================
-- outbox table
-- ============================================================
ALTER TABLE outbox RENAME COLUMN tenant_id TO shop_id;

-- Rebuild index
DROP INDEX IF EXISTS idx_outbox_tenant;
CREATE INDEX idx_outbox_shop ON outbox(shop_id);

-- Update column comment
COMMENT ON COLUMN outbox.shop_id IS 'Shop identifier for multi-tenancy isolation';
