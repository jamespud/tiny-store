-- E2E deterministic seeds
-- Provides initial available stock for fixed SKUs used by tests/api MallE2EIT.
-- Updated to 10000 units for performance/load testing scenarios.

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
