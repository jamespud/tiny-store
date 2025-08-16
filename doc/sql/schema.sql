CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

create function uuid_generate_v7_sql() returns uuid
    language plpgsql
as
$$
DECLARE
    millis bigint;
    bytes  bytea;
BEGIN
    -- 当前时间戳毫秒数（48 bit）
    millis := (extract(epoch FROM clock_timestamp()) * 1000)::bigint;

    -- 初始化 16 字节随机值
    bytes := public.gen_random_bytes(16);

    -- 写入前 6 字节为时间戳
    bytes := overlay(bytes placing decode(lpad(to_hex(millis), 12, '0'), 'hex') from 1 for 6);

    -- 第 7 字节：高 4 bit 为版本号(0111b = 7)
    bytes := set_byte(bytes, 6, (get_byte(bytes, 6) & 0x0F) | 0x70);

    -- 第 9 字节：高 2 bit 为 variant（10xxxxxxb）
    bytes := set_byte(bytes, 8, (get_byte(bytes, 8) & 0x3F) | 0x80);

    RETURN encode(bytes, 'hex')::uuid;
END;
$$;

CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW(); -- 将更新时间设为当前时间
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

alter function update_modified_column() owner to postgres;
alter function uuid_generate_v7_sql() owner to postgres;

------------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS product_db;

SET search_path = product_db;

CREATE TYPE product_status AS ENUM ('ENABLED', 'DISABLED');
CREATE TYPE sku_status AS ENUM ('ENABLED', 'DISABLED');
CREATE TYPE media_type AS ENUM ('main', 'gallery');

CREATE TABLE product_db.product
(
    id          UUID PRIMARY KEY        DEFAULT public.uuid_generate_v7_sql(),
    name        VARCHAR(255)   NOT NULL,
    status      product_status NOT NULL DEFAULT 'ENABLED',
    brand_id    UUID, -- 可选品牌ID（内部关联，非外键）
    category_id UUID, -- 可选分类ID（内部关联，非外键）
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- SKU主档（订单/库存核心引用）
CREATE TABLE product_db.product_sku
(
    id         UUID PRIMARY KEY      DEFAULT public.uuid_generate_v7_sql(),
    product_id UUID         NOT NULL REFERENCES product_db.product (id) ON DELETE CASCADE, -- 服务内外键
    sku_code   VARCHAR(50)  NOT NULL UNIQUE,                                               -- SKU编码唯一
    title      VARCHAR(255) NOT NULL,                                                      -- SKU标题（如"红色-XL"）
    barcode    VARCHAR(50),                                                                -- 条形码
    weight     NUMERIC(10, 2),                                                             -- 重量（kg）
    dimensions VARCHAR(50),                                                                -- 尺寸（如"10x20x30cm"）
    tax_class  VARCHAR(50),                                                                -- 税类（如"STANDARD"）
    status     sku_status   NOT NULL DEFAULT 'ENABLED',
    version    INTEGER      NOT NULL DEFAULT 1,                                            -- 乐观锁
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 当前销售价（简化场景可合并到product_sku）
CREATE TABLE product_db.sku_price
(
    sku_id        UUID PRIMARY KEY REFERENCES product_db.product_sku (id) ON DELETE CASCADE, -- 服务内外键
    price         NUMERIC(10, 2) NOT NULL CHECK (price >= 0),                                -- 单价
    currency      VARCHAR(3)     NOT NULL,                                                   -- 币种（如"USD"）
    price_version INTEGER        NOT NULL DEFAULT 1,                                         -- 价格版本（递增）
    effective_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),                                     -- 生效时间
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (sku_id)                                                                          -- 确保一个SKU只有一个当前价
);

-- 商品媒体（图片等）
CREATE TABLE product_db.product_media
(
    id         UUID PRIMARY KEY     DEFAULT public.uuid_generate_v7_sql(),
    product_id UUID        NOT NULL REFERENCES product_db.product (id) ON DELETE CASCADE,
    url        TEXT        NOT NULL,           -- 媒体URL
    type       media_type  NOT NULL,           -- 主图/ gallery
    sort_order INTEGER     NOT NULL DEFAULT 0, -- 排序权重
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 价格变更历史（审计用）
CREATE TABLE product_db.price_history
(
    id            UUID PRIMARY KEY        DEFAULT public.uuid_generate_v7_sql(),
    sku_id        UUID           NOT NULL REFERENCES product_db.product_sku (id) ON DELETE CASCADE,
    price         NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    currency      VARCHAR(3)     NOT NULL,
    price_version INTEGER        NOT NULL,
    start_at      TIMESTAMPTZ    NOT NULL, -- 开始时间
    end_at        TIMESTAMPTZ,             -- 结束时间（NULL表示当前有效）
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

------------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS inventory_db;

SET search_path = inventory_db;

CREATE TYPE reservation_status AS ENUM ('PENDING', 'CONFIRMED', 'RELEASED', 'EXPIRED');
CREATE TYPE movement_type AS ENUM ('IN', 'OUT', 'ADJUST', 'RESERVE', 'RELEASE', 'COMMIT');

-- 库存现势表（按SKU+仓库维度）
CREATE TABLE inventory_db.stock
(
    id          UUID PRIMARY KEY     DEFAULT public.uuid_generate_v7_sql(),
    sku_id      UUID        NOT NULL,
    location_id UUID        NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001', -- 默认仓库ID
    total       INTEGER     NOT NULL CHECK (total >= 0),                             -- 总库存
    reserved    INTEGER     NOT NULL DEFAULT 0 CHECK (reserved >= 0),                -- 已预留
    version     INTEGER     NOT NULL DEFAULT 1,                                      -- 乐观锁
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (total >= reserved)                                                        -- 可用库存（total - reserved）非负
);

-- 库存预留记录（订单未支付时的临时占用）
CREATE TABLE inventory_db.stock_reservation
(
    id              UUID PRIMARY KEY            DEFAULT public.uuid_generate_v7_sql(),
    order_id        UUID               NOT NULL,                      -- 关联订单ID（跨服务，无外键）
    sku_id          UUID               NOT NULL,
    location_id     UUID               NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    quantity        INTEGER            NOT NULL CHECK (quantity > 0), -- 预留数量
    status          reservation_status NOT NULL DEFAULT 'PENDING',
    expire_at       TIMESTAMPTZ        NOT NULL,                      -- 预留过期时间
    idempotency_key VARCHAR(100)       NOT NULL UNIQUE,               -- 幂等键（防重复）
    created_at      TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

-- 库存变动流水（审计用）
CREATE TABLE inventory_db.stock_movement
(
    id          UUID PRIMARY KEY       DEFAULT public.uuid_generate_v7_sql(),
    sku_id      UUID          NOT NULL,
    location_id UUID          NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    delta       INTEGER       NOT NULL, -- 变动量（+入库/-出库）
    type        movement_type NOT NULL, -- 变动类型
    reason      VARCHAR(255)  NOT NULL, -- 变动原因（如"订单支付扣减"）
    ref_id      UUID,                   -- 关联ID（如订单ID/预留ID）
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

----------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS order_db;

SET search_path = order_db;

CREATE TYPE order_status AS ENUM ('PENDING', 'PAID', 'CANCELED', 'SHIPPED', 'COMPLETED');
CREATE TYPE payment_status AS ENUM ('NONE', 'REQUIRING_ACTION', 'PAID', 'FAILED');
CREATE TYPE outbox_status AS ENUM ('NEW', 'PUBLISHED', 'FAILED');
CREATE TYPE coupon_type AS ENUM ('AMOUNT', 'PERCENTAGE'); -- 优惠券类型
CREATE TYPE coupon_status AS ENUM ('ACTIVE', 'EXPIRED', 'USED'); -- 优惠券状态

-- 订单主表
CREATE TABLE order_db.order
(
    id                    UUID PRIMARY KEY        DEFAULT public.uuid_generate_v7_sql(),
    user_id               UUID           NOT NULL,                                        -- 下单用户ID
    status                order_status   NOT NULL DEFAULT 'PENDING',
    currency              VARCHAR(3)     NOT NULL,                                        -- 订单币种
    total_amount          NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0),              -- 订单总金额
    items_amount          NUMERIC(10, 2) NOT NULL CHECK (items_amount >= 0),              -- 商品总金额
    discount_amount       NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0), -- 折扣金额
    tax_amount            NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),      -- 税费
    shipping_amount       NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (shipping_amount >= 0), -- 运费
    payment_status        payment_status NOT NULL DEFAULT 'NONE',
    shipping_address_json JSONB          NOT NULL,                                        -- 收货地址（JSON结构）
    billing_address_json  JSONB,                                                          -- 账单地址（可选）
    payment_intent_id     UUID,                                                           -- 关联支付意图ID（跨服务，无外键）
    idempotency_key       VARCHAR(100)   NOT NULL UNIQUE,                                 -- 幂等键（防重复下单）
    version               INTEGER        NOT NULL DEFAULT 1,                              -- 乐观锁
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- 订单项（含快照）
CREATE TABLE order_db.order_item
(
    id                    UUID PRIMARY KEY        DEFAULT public.uuid_generate_v7_sql(),
    order_id              UUID           NOT NULL REFERENCES order_db.order (id) ON DELETE CASCADE,
    sku_id                UUID           NOT NULL,                                        -- 关联SKU（跨服务）
    product_id            UUID           NOT NULL,                                        -- 关联商品（跨服务）
    product_name_snapshot VARCHAR(255)   NOT NULL,                                        -- 商品名称快照
    sku_title_snapshot    VARCHAR(255)   NOT NULL,                                        -- SKU标题快照
    unit_price            NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),                -- 单价快照
    currency              VARCHAR(3)     NOT NULL,                                        -- 币种
    price_version         INTEGER        NOT NULL,                                        -- 价格版本（追溯用）
    qty                   INTEGER        NOT NULL CHECK (qty > 0),                        -- 数量
    subtotal_amount       NUMERIC(10, 2) NOT NULL CHECK (subtotal_amount >= 0),           -- 小计（未折扣）
    discount_amount       NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0), -- 单品折扣
    tax_amount            NUMERIC(10, 2) NOT NULL DEFAULT 0 CHECK (tax_amount >= 0),      -- 单品税费
    total_amount          NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0)               -- 单品总金额
);

-- 订单状态变更历史
CREATE TABLE order_db.order_status_history
(
    id          UUID PRIMARY KEY      DEFAULT public.uuid_generate_v7_sql(),
    order_id    UUID         NOT NULL REFERENCES order_db.order (id) ON DELETE CASCADE,
    from_status order_status,          -- 变更前状态（NULL表示初始状态）
    to_status   order_status NOT NULL, -- 变更后状态
    reason      VARCHAR(255) NOT NULL, -- 变更原因（如"用户支付"）
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 订单事件Outbox（确保消息可靠发送）
CREATE TABLE order_db.order_outbox
(
    id           UUID PRIMARY KEY       DEFAULT public.uuid_generate_v7_sql(),
    aggregate_id UUID          NOT NULL, -- 关联订单ID
    event_type   VARCHAR(50)   NOT NULL, -- 事件类型（如"OrderCreated"）
    payload_json JSONB         NOT NULL, -- 事件内容
    status       outbox_status NOT NULL DEFAULT 'NEW',
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- 购物车主表
CREATE TABLE order_db.cart
(
    id         UUID PRIMARY KEY     DEFAULT public.uuid_generate_v7_sql(),
    user_id    UUID,                                      -- 登录用户ID（可选，未登录则为NULL）
    session_id VARCHAR(100),                              -- 会话ID（未登录用户标识）
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (user_id IS NOT NULL OR session_id IS NOT NULL) -- 至少有一个标识
);

-- 购物车项
CREATE TABLE order_db.cart_item
(
    id             UUID PRIMARY KEY     DEFAULT public.uuid_generate_v7_sql(),
    cart_id        UUID        NOT NULL REFERENCES order_db.cart (id) ON DELETE CASCADE,
    sku_id         UUID        NOT NULL,                 -- 关联SKU（跨服务，无外键）
    qty            INTEGER     NOT NULL CHECK (qty > 0), -- 数量
    price_snapshot NUMERIC(10, 2),                       -- 价格快照（仅展示用）
    currency       VARCHAR(3),                           -- 币种快照
    added_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 优惠券
CREATE TABLE order_db.coupon
(
    id          UUID PRIMARY KEY     DEFAULT public.uuid_generate_v7_sql(),
    code        VARCHAR(50) NOT NULL UNIQUE, -- 优惠码
    description TEXT,                        -- 描述
    discount    NUMERIC(10, 2) NOT NULL CHECK (discount >= 0), -- 折扣金额或百分比
    type        VARCHAR(20) NOT NULL,       -- 折扣类型（如"AMOUNT"或"PERCENTAGE"）
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- 状态（如"ACTIVE", "EXPIRED", "USED"）
    start_at    TIMESTAMPTZ NOT NULL,       -- 生效时间
    end_at      TIMESTAMPTZ NOT NULL,       -- 失效时间
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
----------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS payment_db;

SET search_path = payment_db;

CREATE TYPE intent_status AS ENUM ('REQUIRES_PAYMENT', 'REQUIRES_ACTION', 'SUCCEEDED', 'CANCELED', 'FAILED');
CREATE TYPE transaction_type AS ENUM ('AUTH', 'CAPTURE', 'SALE', 'REFUND');
CREATE TYPE transaction_status AS ENUM ('PENDING', 'SUCCEEDED', 'FAILED');
CREATE TYPE outbox_status AS ENUM ('NEW', 'PUBLISHED', 'FAILED');

-- 支付意图（订单与支付的桥梁）
CREATE TABLE payment_db.payment_intent
(
    id                     UUID PRIMARY KEY        DEFAULT public.uuid_generate_v7_sql(),
    order_id               UUID           NOT NULL UNIQUE,              -- 关联订单ID（一单一意图）
    amount                 NUMERIC(10, 2) NOT NULL CHECK (amount >= 0), -- 支付金额
    currency               VARCHAR(3)     NOT NULL,                     -- 币种
    status                 intent_status  NOT NULL DEFAULT 'REQUIRES_PAYMENT',
    provider               VARCHAR(50)    NOT NULL,                     -- 支付网关（如"Stripe"）
    provider_client_secret VARCHAR(255),                                -- 网关客户端密钥（前端调起支付用）
    idempotency_key        VARCHAR(100)   NOT NULL UNIQUE,              -- 幂等键
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- 支付交易明细（网关交互记录）
CREATE TABLE payment_db.payment_transaction
(
    id               UUID PRIMARY KEY            DEFAULT public.uuid_generate_v7_sql(),
    intent_id        UUID               NOT NULL REFERENCES payment_db.payment_intent (id) ON DELETE CASCADE,
    provider_txn_id  VARCHAR(100),                                    -- 网关交易ID（如Stripe的txn_id）
    type             transaction_type   NOT NULL,                     -- 交易类型（授权/捕获/退款等）
    amount           NUMERIC(10, 2)     NOT NULL CHECK (amount >= 0), -- 交易金额
    status           transaction_status NOT NULL,
    raw_payload_json JSONB              NOT NULL,                     -- 网关原始响应（调试用）
    created_at       TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

-- 支付事件Outbox
CREATE TABLE payment_db.payment_outbox
(
    id           UUID PRIMARY KEY              DEFAULT public.uuid_generate_v7_sql(),
    aggregate_id UUID                 NOT NULL, -- 关联支付意图ID
    event_type   VARCHAR(50)          NOT NULL, -- 如"PaymentSucceeded"
    payload_json JSONB                NOT NULL,
    status       outbox_status NOT NULL DEFAULT 'NEW',
    created_at   TIMESTAMPTZ          NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ          NOT NULL DEFAULT NOW()
);

-------------------------------------------------------------------------------

