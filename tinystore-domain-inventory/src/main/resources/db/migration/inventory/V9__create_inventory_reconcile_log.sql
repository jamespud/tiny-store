-- V9: Reconciliation audit log (append-only). One row per desync/repair action.
CREATE TABLE tinystore_inventory.inventory_reconcile_log (
    id                    BIGSERIAL PRIMARY KEY,
    shop_id               VARCHAR(100) NOT NULL,
    sku_id                VARCHAR(128) NOT NULL,
    db_total              BIGINT NOT NULL,
    db_confirmed          BIGINT NOT NULL,
    db_pre_deducted       BIGINT NOT NULL,
    redis_total_before    BIGINT,
    redis_deducted_before BIGINT,
    target_total          BIGINT NOT NULL,
    target_deducted       BIGINT NOT NULL,
    action                VARCHAR(32) NOT NULL,
    repaired_fields       VARCHAR(64),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reconcile_log_created_at
    ON tinystore_inventory.inventory_reconcile_log (created_at DESC);
CREATE INDEX idx_reconcile_log_shop_sku
    ON tinystore_inventory.inventory_reconcile_log (shop_id, sku_id, created_at DESC);
