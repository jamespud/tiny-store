-- Inventory 模块 DDL 占位 (与 docs/inventory/SCHEMA.md 一致)
CREATE TABLE IF NOT EXISTS inventory_stock (
    id BIGSERIAL PRIMARY KEY,
    shop_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(128) NOT NULL,
    total_quantity BIGINT NOT NULL,
    reserved_quantity BIGINT NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_stock_shop_sku UNIQUE (shop_id, sku_id)
);

CREATE TABLE IF NOT EXISTS inventory_reservation (
    reservation_id VARCHAR(64) PRIMARY KEY,
    shop_id VARCHAR(64) NOT NULL,
    sku_id VARCHAR(128) NOT NULL,
    quantity BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL,
    expire_at TIMESTAMP NOT NULL,
    operation_id VARCHAR(128),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL,
    release_reason VARCHAR(255),
    CONSTRAINT uk_resv_operation UNIQUE (operation_id)
);

CREATE INDEX IF NOT EXISTS idx_resv_state_expire ON inventory_reservation(state, expire_at);

