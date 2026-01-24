ALTER TABLE sku ADD COLUMN IF NOT EXISTS sku_id VARCHAR(100);
ALTER TABLE sku ADD COLUMN IF NOT EXISTS merchant_id VARCHAR(100);
ALTER TABLE sku ADD COLUMN IF NOT EXISTS sku_name VARCHAR(500);
ALTER TABLE sku ADD COLUMN IF NOT EXISTS spec_json jsonb;
ALTER TABLE sku ADD COLUMN IF NOT EXISTS weight_grams BIGINT;
ALTER TABLE sku ADD COLUMN IF NOT EXISTS unit_price_cents BIGINT;
ALTER TABLE sku ADD COLUMN IF NOT EXISTS promote_price_cents BIGINT;

UPDATE sku SET sku_id = md5(id::text || '-' || now()::text) WHERE sku_id IS NULL;
UPDATE sku SET unit_price_cents = COALESCE((price * 100)::bigint, 0) WHERE unit_price_cents IS NULL;
UPDATE sku SET promote_price_cents = 0 WHERE promote_price_cents IS NULL;
UPDATE sku SET weight_grams = 0 WHERE weight_grams IS NULL;
UPDATE sku SET spec_json = '{}'::jsonb WHERE spec_json IS NULL;

ALTER TABLE sku ALTER COLUMN sku_id SET NOT NULL;
ALTER TABLE sku ALTER COLUMN unit_price_cents SET NOT NULL;
ALTER TABLE sku ALTER COLUMN promote_price_cents SET NOT NULL;
ALTER TABLE sku ALTER COLUMN weight_grams SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sku_tenant_sku_id ON sku(tenant_id, sku_id);

