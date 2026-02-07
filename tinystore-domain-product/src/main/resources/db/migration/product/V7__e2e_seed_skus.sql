-- E2E deterministic seeds
-- Provides fixed contract data used by tests/api MallE2EIT:
--   SHOP_A/SKU_A -> prod-1
--   SHOP_B/SKU_B -> prod-2

-- Seed products
INSERT INTO product (shop_id, product_id, name, status, category_id, description)
VALUES
  ('SHOP_A', 'prod-1', 'Product A', 'ONLINE', NULL, 'E2E seed product A'),
  ('SHOP_B', 'prod-2', 'Product B', 'ONLINE', NULL, 'E2E seed product B')
ON CONFLICT (shop_id, product_id) DO NOTHING;

-- Seed SKUs
INSERT INTO sku (
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
  (
    'SHOP_A',
    'SKU_A',
    'prod-1',
    'DEFAULT',
    'AVAILABLE',
    100,
    1000,
    0,
    0,
    '{}'::jsonb,
    'SKU A',
    'seller-A'
  ),
  (
    'SHOP_B',
    'SKU_B',
    'prod-2',
    'DEFAULT',
    'AVAILABLE',
    100,
    2000,
    0,
    0,
    '{}'::jsonb,
    'SKU B',
    'seller-B'
  )
ON CONFLICT (shop_id, sku_id) DO NOTHING;
