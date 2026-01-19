CREATE TABLE IF NOT EXISTS order_main
(
    id                 BIGSERIAL PRIMARY KEY,
    order_no           VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(100) NOT NULL,
    user_id            VARCHAR(64)  NOT NULL,

    core_flow_status   VARCHAR(32)  NOT NULL,
    payment_status     VARCHAR(32)  NOT NULL,
    fulfillment_status VARCHAR(32)  NOT NULL,
    after_sale_status  VARCHAR(32)  NOT NULL,

    total_amount       BIGINT       NOT NULL DEFAULT 0,
    discount_amount    BIGINT       NOT NULL DEFAULT 0,
    payable_amount     BIGINT       NOT NULL DEFAULT 0,
    currency           VARCHAR(8)   NOT NULL DEFAULT 'CNY',

    address_id         VARCHAR(64),
    address_snapshot   JSONB,

    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_order_main_order_no UNIQUE (order_no)
);

CREATE INDEX IF NOT EXISTS idx_order_main_user_created_at ON order_main (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_main_tenant_created_at ON order_main (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_order_main_status_created_at ON order_main (core_flow_status, created_at DESC);


CREATE TABLE IF NOT EXISTS order_sub
(
    id                 BIGSERIAL PRIMARY KEY,
    sub_order_no       VARCHAR(64)  NOT NULL,
    main_order_no      VARCHAR(64)  NOT NULL,
    tenant_id          VARCHAR(100) NOT NULL,
    merchant_id        VARCHAR(64),
    merchant_name      VARCHAR(128),

    core_flow_status   VARCHAR(32)  NOT NULL,
    payment_status     VARCHAR(32)  NOT NULL,
    fulfillment_status VARCHAR(32)  NOT NULL,
    after_sale_status  VARCHAR(32)  NOT NULL,

    logistics_company_code VARCHAR(64),
    logistics_company_name VARCHAR(128),
    tracking_no            VARCHAR(128),
    shipped_at             TIMESTAMPTZ,
    delivered_at           TIMESTAMPTZ,
    received_at            TIMESTAMPTZ,

    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_order_sub_order_no UNIQUE (sub_order_no),
    CONSTRAINT fk_order_sub_main_order_no FOREIGN KEY (main_order_no) REFERENCES order_main (order_no)
);

CREATE INDEX IF NOT EXISTS idx_order_sub_main_order_no ON order_sub (main_order_no);
CREATE INDEX IF NOT EXISTS idx_order_sub_merchant_created_at ON order_sub (merchant_id, created_at DESC);


CREATE TABLE IF NOT EXISTS order_item
(
    id                 BIGSERIAL PRIMARY KEY,
    main_order_no      VARCHAR(64)  NOT NULL,
    sub_order_no       VARCHAR(64),
    line_no            INTEGER      NOT NULL,

    sku_id             VARCHAR(100) NOT NULL,
    quantity           INTEGER      NOT NULL,

    unit_price_amount      BIGINT   NOT NULL DEFAULT 0,
    line_total_amount      BIGINT   NOT NULL DEFAULT 0,
    line_discount_amount   BIGINT   NOT NULL DEFAULT 0,
    line_payable_amount    BIGINT   NOT NULL DEFAULT 0,
    currency              VARCHAR(8) NOT NULL DEFAULT 'CNY',

    sku_snapshot       JSONB        NOT NULL,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_order_item_line UNIQUE (main_order_no, line_no),
    CONSTRAINT fk_order_item_main_order_no FOREIGN KEY (main_order_no) REFERENCES order_main (order_no),
    CONSTRAINT fk_order_item_sub_order_no FOREIGN KEY (sub_order_no) REFERENCES order_sub (sub_order_no)
);

CREATE INDEX IF NOT EXISTS idx_order_item_main_line ON order_item (main_order_no, line_no);
CREATE INDEX IF NOT EXISTS idx_order_item_sub_order_no ON order_item (sub_order_no);
CREATE INDEX IF NOT EXISTS idx_order_item_sku_id ON order_item (sku_id);

