CREATE TABLE IF NOT EXISTS tinystore_inventory.inventory_stock
(
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    sku_id           VARCHAR(128) NOT NULL,
    total_quantity   BIGINT       NOT NULL,
    reserved_quantity BIGINT      NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_stock_tenant_sku UNIQUE (tenant_id, sku_id),
    CONSTRAINT chk_stock_total_nonneg CHECK (total_quantity >= 0),
    CONSTRAINT chk_stock_reserved_nonneg CHECK (reserved_quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_tenant_sku
    ON tinystore_inventory.inventory_stock (tenant_id, sku_id);


CREATE TABLE IF NOT EXISTS tinystore_inventory.inventory_reservation
(
    reservation_id   VARCHAR(64)  PRIMARY KEY,
    tenant_id        VARCHAR(100) NOT NULL,
    sku_id           VARCHAR(128) NOT NULL,
    quantity         BIGINT       NOT NULL,
    status           VARCHAR(32)  NOT NULL,
    expire_at        TIMESTAMPTZ  NOT NULL,
    order_no         VARCHAR(64)  NOT NULL,
    operation_id     VARCHAR(128) NOT NULL,
    release_reason   VARCHAR(255),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_reservation_operation_sku UNIQUE (operation_id, sku_id),
    CONSTRAINT chk_reservation_qty_positive CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_reservation_tenant_sku
    ON tinystore_inventory.inventory_reservation (tenant_id, sku_id);

CREATE INDEX IF NOT EXISTS idx_reservation_status_expire
    ON tinystore_inventory.inventory_reservation (status, expire_at);

CREATE INDEX IF NOT EXISTS idx_reservation_order_no
    ON tinystore_inventory.inventory_reservation (order_no);
