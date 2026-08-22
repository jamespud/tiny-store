-- E2E deterministic seeds (externalized from core Flyway migrations, applied by SeedData at test start).
-- Provides fixed contract data used by tests/api E2E:
--   SHOP_A/SKU_A -> prod-1, SHOP_B/SKU_B -> prod-2

-- Inventory stock (schema: tinystore_inventory)
INSERT INTO tinystore_inventory.inventory_stock (
  shop_id,
  sku_id,
  total_quantity,
  reserved_quantity,
  version
)
VALUES
  ('SHOP_A', 'SKU_A', 1000000, 0, 0),
  ('SHOP_B', 'SKU_B', 1000000, 0, 0)
ON CONFLICT (shop_id, sku_id) DO NOTHING;

-- Products (schema: tinystore_product)
INSERT INTO tinystore_product.product (shop_id, product_id, name, status, category_id, description)
VALUES
  ('SHOP_A', 'prod-1', 'Product A', 'ONLINE', NULL, 'E2E seed product A'),
  ('SHOP_B', 'prod-2', 'Product B', 'ONLINE', NULL, 'E2E seed product B')
ON CONFLICT (shop_id, product_id) DO NOTHING;

-- SKUs (schema: tinystore_product)
INSERT INTO tinystore_product.sku (
  shop_id,
  sku_id,
  product_id,
  spec_combination,
  status,
  stock,
  unit_price_cents,
  promote_price_cents,
  weight_grams,
  spec_json,
  sku_name,
  merchant_id
)
VALUES
  ('SHOP_A', 'SKU_A', 'prod-1', 'DEFAULT', 'AVAILABLE', 100, 1000, 0, 0, '{}'::jsonb, 'SKU A', 'seller-A'),
  ('SHOP_B', 'SKU_B', 'prod-2', 'DEFAULT', 'AVAILABLE', 100, 2000, 0, 0, '{}'::jsonb, 'SKU B', 'seller-B')
ON CONFLICT (shop_id, sku_id) DO NOTHING;
