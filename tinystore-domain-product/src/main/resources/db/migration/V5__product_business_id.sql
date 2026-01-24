ALTER TABLE product ADD COLUMN IF NOT EXISTS product_id VARCHAR(100);

UPDATE product SET product_id = md5(id::text || '-' || now()::text) WHERE product_id IS NULL;

ALTER TABLE product ALTER COLUMN product_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_product_tenant_product_id ON product(tenant_id, product_id);

