-- 库存扣减流水/占用记录表（Redis 扣减 + DB 流水最小一致性保障）
CREATE TABLE IF NOT EXISTS tinystore_inventory.inventory_deduct_record
(
    id              BIGSERIAL PRIMARY KEY,
    order_id        VARCHAR(128)  NOT NULL,
    idempotency_key VARCHAR(255)  NOT NULL,
    shop_id         VARCHAR(100)  NOT NULL,
    sku_id          VARCHAR(128)  NOT NULL,
    occupy_id       VARCHAR(512)  NOT NULL,
    quantity        INT           NOT NULL,
    status          VARCHAR(32)   NOT NULL DEFAULT 'DEDUCTED',
    release_reason  VARCHAR(128),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_deduct_occupy UNIQUE (shop_id, sku_id, occupy_id)
);

CREATE INDEX IF NOT EXISTS idx_deduct_record_order_id
    ON tinystore_inventory.inventory_deduct_record (order_id);

CREATE INDEX IF NOT EXISTS idx_deduct_record_idempotency
    ON tinystore_inventory.inventory_deduct_record (idempotency_key);
