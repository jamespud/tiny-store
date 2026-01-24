CREATE TABLE IF NOT EXISTS tinystore_inventory.inventory_adjustment
(
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(100) NOT NULL,
    sku_id      VARCHAR(128) NOT NULL,
    delta_total BIGINT       NOT NULL,
    reason      VARCHAR(64)  NOT NULL,
    reference_id VARCHAR(128),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_adjust_reason_ref UNIQUE (reason, reference_id)
);

CREATE INDEX IF NOT EXISTS idx_adjust_tenant_sku_created_at
    ON tinystore_inventory.inventory_adjustment (tenant_id, sku_id, created_at DESC);
