-- 扩展：启用UUID支持和密码加密函数
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext; 

-- 创建安全相关的schema
CREATE SCHEMA IF NOT EXISTS security;
SET search_path TO security;

-- 更新时间触发器函数
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 1. 用户表
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    username CITEXT NOT NULL UNIQUE, -- CITEXT类型支持不区分大小写查询
    password TEXT NOT NULL, -- 存储bcrypt加密后的密码
    email CITEXT NOT NULL UNIQUE,
    full_name TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    last_password_change_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 添加邮箱格式验证
    CONSTRAINT valid_email CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- 为users表添加更新时间触发器
CREATE TRIGGER update_user_modtime
BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_modified_column();

-- 2. 角色表
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    name TEXT NOT NULL UNIQUE, -- 角色名称，建议使用ROLE_前缀
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. 用户角色关联表（多对多）
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    assigned_by UUID REFERENCES users(id), -- 记录谁分配的角色
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

-- 4. 权限表
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    name TEXT NOT NULL UNIQUE, -- 权限名称，建议使用资源:操作格式，如user:read
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 5. 角色权限关联表（多对多）
CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 6. OAuth2客户端表（Spring Authorization Server专用）
CREATE TABLE oauth2_registered_client (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    client_id TEXT NOT NULL UNIQUE,
    client_id_issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    client_secret TEXT, -- 可以为NULL，公开客户端
    client_secret_expires_at TIMESTAMPTZ,
    client_name TEXT NOT NULL,
    client_authentication_methods JSONB NOT NULL, -- 使用JSONB存储认证方法
    authorization_grant_types JSONB NOT NULL, -- 使用JSONB存储授权类型
    redirect_uris JSONB, -- 使用JSONB存储重定向URI数组
    post_logout_redirect_uris JSONB, -- 使用JSONB存储登出重定向URI数组
    scopes JSONB NOT NULL, -- 使用JSONB存储权限范围数组
    client_settings JSONB NOT NULL, -- 客户端设置
    token_settings JSONB NOT NULL, -- 令牌设置
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 为oauth2_registered_client表添加更新时间触发器
CREATE TRIGGER update_oauth2_client_modtime
BEFORE UPDATE ON oauth2_registered_client
FOR EACH ROW EXECUTE FUNCTION update_modified_column();

-- 7. OAuth2授权表
CREATE TABLE oauth2_authorization (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    registered_client_id UUID NOT NULL,
    principal_name TEXT NOT NULL,
    authorization_grant_type TEXT NOT NULL,
    authorized_scopes JSONB,
    attributes JSONB,
    state TEXT,
    -- 授权码相关
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ,
    authorization_code_metadata JSONB,
    -- 访问令牌相关
    access_token_value TEXT,
    access_token_issued_at TIMESTAMPTZ,
    access_token_expires_at TIMESTAMPTZ,
    access_token_metadata JSONB,
    access_token_type TEXT,
    access_token_scopes JSONB,
    -- OIDC ID令牌相关
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMPTZ,
    oidc_id_token_expires_at TIMESTAMPTZ,
    oidc_id_token_metadata JSONB,
    -- 刷新令牌相关
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMPTZ,
    refresh_token_expires_at TIMESTAMPTZ,
    refresh_token_metadata JSONB,
    -- 用户码相关
    user_code_value TEXT,
    user_code_issued_at TIMESTAMPTZ,
    user_code_expires_at TIMESTAMPTZ,
    user_code_metadata JSONB,
    -- 设备码相关
    device_code_value TEXT,
    device_code_issued_at TIMESTAMPTZ,
    device_code_expires_at TIMESTAMPTZ,
    device_code_metadata JSONB,
    FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client(id),
    -- 添加索引优化查询
    CONSTRAINT uk_authorization_state UNIQUE (state)
);

-- 8. OAuth2授权确认表
CREATE TABLE oauth2_authorization_consent (
    registered_client_id UUID NOT NULL,
    principal_name TEXT NOT NULL,
    authorities JSONB NOT NULL, -- 使用JSONB存储权限数组
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (registered_client_id, principal_name),
    FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client(id)
);

-- 为oauth2_authorization_consent表添加更新时间触发器
CREATE TRIGGER update_oauth2_consent_modtime
BEFORE UPDATE ON oauth2_authorization_consent
FOR EACH ROW EXECUTE FUNCTION update_modified_column();

-- 9. 用户登录历史表
CREATE TABLE user_login_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id UUID NOT NULL,
    login_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    logout_time TIMESTAMPTZ,
    ip_address TEXT,
    -- 使用inet类型存储IP地址，支持IP范围查询
    ip_address_inet INET GENERATED ALWAYS AS (ip_address::inet) STORED,
    user_agent TEXT,
    login_success BOOLEAN NOT NULL,
    failure_reason TEXT,
    session_id TEXT,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- 10. 用户密码历史表（用于防止密码重复使用）
CREATE TABLE user_password_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    user_id UUID NOT NULL,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 创建索引优化查询性能
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_role_permissions_role_id ON role_permissions(role_id);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);
CREATE INDEX idx_oauth2_authorization_client_id ON oauth2_authorization(registered_client_id);
CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization(principal_name);
CREATE INDEX idx_oauth2_authorization_access_token ON oauth2_authorization(access_token_value) WHERE access_token_value IS NOT NULL;
CREATE INDEX idx_oauth2_authorization_refresh_token ON oauth2_authorization(refresh_token_value) WHERE refresh_token_value IS NOT NULL;
CREATE INDEX idx_user_login_history_user_id ON user_login_history(user_id);
CREATE INDEX idx_user_login_history_login_time ON user_login_history(login_time);
CREATE INDEX idx_user_password_history_user_id ON user_password_history(user_id);

-- 创建视图：用户权限视图，方便查询用户拥有的所有权限
CREATE OR REPLACE VIEW user_effective_permissions AS
SELECT DISTINCT
    u.id AS user_id,
    u.username,
    p.id AS permission_id,
    p.name AS permission_name
FROM
    users u
    JOIN user_roles ur ON u.id = ur.user_id
    JOIN roles r ON ur.role_id = r.id
    JOIN role_permissions rp ON r.id = rp.role_id
    JOIN permissions p ON rp.permission_id = p.id;

-- 初始数据：默认角色
INSERT INTO roles (name, description) VALUES
('ROLE_ADMIN', '系统管理员，拥有所有权限'),
('ROLE_USER', '普通用户，拥有基础权限')
ON CONFLICT (name) DO NOTHING;

-- 初始数据：默认权限
INSERT INTO permissions (name, description) VALUES
('user:read', '查看用户信息'),
('user:write', '创建和修改用户'),
('user:delete', '删除用户'),
('role:read', '查看角色信息'),
('role:write', '创建和修改角色')
ON CONFLICT (name) DO NOTHING;

-- === Improvements appended below ===

-- 1. Enforce + track password changes (prevent reuse of last N, update timestamp, keep history)
CREATE OR REPLACE FUNCTION enforce_and_track_password_change()
RETURNS TRIGGER AS $$
DECLARE
    reuse_count INT;
    password_reuse_window INT := 5; -- configurable window (last N passwords)
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.password IS DISTINCT FROM OLD.password THEN
        -- Check recent password reuse
        SELECT COUNT(*) INTO reuse_count
        FROM (
            SELECT password_hash
            FROM user_password_history
            WHERE user_id = OLD.id
            ORDER BY created_at DESC
            LIMIT password_reuse_window
        ) recent
        WHERE recent.password_hash = NEW.password;

        IF reuse_count > 0 THEN
            RAISE EXCEPTION 'Password was used in the last % times', password_reuse_window;
        END IF;

        -- Track history (store NEW hashed password)
        INSERT INTO user_password_history (user_id, password_hash)
        VALUES (OLD.id, NEW.password);

        -- Update last change timestamp
        NEW.last_password_change_at = NOW();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_password_change ON users;
CREATE TRIGGER trg_users_password_change
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION enforce_and_track_password_change();

-- 2. Strengthen JSON structure for oauth2_registered_client
ALTER TABLE oauth2_registered_client
    ALTER COLUMN client_authentication_methods SET DEFAULT '[]'::jsonb,
    ALTER COLUMN authorization_grant_types SET DEFAULT '[]'::jsonb,
    ALTER COLUMN redirect_uris SET DEFAULT '[]'::jsonb,
    ALTER COLUMN post_logout_redirect_uris SET DEFAULT '[]'::jsonb,
    ALTER COLUMN scopes SET DEFAULT '[]'::jsonb,
    ALTER COLUMN client_settings SET DEFAULT '{}'::jsonb,
    ALTER COLUMN token_settings SET DEFAULT '{}'::jsonb;

ALTER TABLE oauth2_registered_client
    ADD CONSTRAINT chk_oauth2_client_auth_methods_array CHECK (jsonb_typeof(client_authentication_methods) = 'array'),
    ADD CONSTRAINT chk_oauth2_client_grant_types_array CHECK (jsonb_typeof(authorization_grant_types) = 'array'),
    ADD CONSTRAINT chk_oauth2_client_redirect_uris_array CHECK (redirect_uris IS NULL OR jsonb_typeof(redirect_uris) = 'array'),
    ADD CONSTRAINT chk_oauth2_client_post_logout_uris_array CHECK (post_logout_redirect_uris IS NULL OR jsonb_typeof(post_logout_redirect_uris) = 'array'),
    ADD CONSTRAINT chk_oauth2_client_scopes_array CHECK (jsonb_typeof(scopes) = 'array');

-- 3. Add ON DELETE CASCADE for related oauth2_authorization when client is removed
ALTER TABLE oauth2_authorization
    DROP CONSTRAINT IF EXISTS oauth2_authorization_registered_client_id_fkey,
    ADD CONSTRAINT oauth2_authorization_registered_client_id_fkey
        FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client(id)
        ON DELETE CASCADE;

-- 4. Normalize IP storage (use inet directly; drop generated column)
ALTER TABLE user_login_history
    DROP COLUMN IF EXISTS ip_address_inet;
ALTER TABLE user_login_history
    ALTER COLUMN ip_address TYPE inet USING ip_address::inet;
-- Optional index for ip queries
CREATE INDEX IF NOT EXISTS idx_user_login_history_ip ON user_login_history (ip_address);

-- 5. Additional indexes for maintenance / cleanup
CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_access_token_expires_at
    ON oauth2_authorization (access_token_expires_at);
CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_refresh_token_expires_at
    ON oauth2_authorization (refresh_token_expires_at);

-- 6. (Optional) Partition large append-only tables (example shown, commented)
-- To enable later:
-- ALTER TABLE user_login_history PARTITION BY RANGE (login_time);
-- CREATE TABLE user_login_history_2025_01 PARTITION OF user_login_history
--     FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

-- 7. Row Level Security (RLS) example (enable only if direct SQL access by app roles)
-- ALTER TABLE users ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY users_self ON users
--     USING (id = current_setting('app.current_user_id', true)::uuid);

-- 8. Security note: Consider storing only hashed token values (access/refresh)
-- using digest(access_token_value, 'sha256') and indexing hashes, to mitigate leakage risk.

-- 9. Integrity: Ensure no NULL password (already NOT NULL) & minimal length example
ALTER TABLE users
    ADD CONSTRAINT chk_users_password_min_length CHECK (length(password) >= 60); -- bcrypt hash length guard

-- 10. View to aggregate permissions per user (JSON)
CREATE OR REPLACE VIEW user_permissions_agg AS
SELECT u.id AS user_id,
       u.username,
       jsonb_agg(DISTINCT p.name ORDER BY p.name) AS permissions
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
LEFT JOIN role_permissions rp ON r.id = rp.role_id
LEFT JOIN permissions p ON rp.permission_id = p.id
GROUP BY u.id, u.username;

-- 11. Helper function to purge expired authorizations (call via cron / pgagent)
CREATE OR REPLACE FUNCTION purge_expired_authorizations()
RETURNS INTEGER AS $$
DECLARE
    v_count INT;
BEGIN
    DELETE FROM oauth2_authorization
    WHERE (access_token_expires_at IS NOT NULL AND access_token_expires_at < NOW())
       OR (refresh_token_expires_at IS NOT NULL AND refresh_token_expires_at < NOW())
       OR (authorization_code_expires_at IS NOT NULL AND authorization_code_expires_at < NOW());
    GET DIAGNOSTICS v_count = ROW_COUNT;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- 12. (Optional) Policy: limit login history retention (example 180 days)
-- DELETE FROM user_login_history WHERE login_time < NOW() - INTERVAL '180 days';

-- End of improvements

/* =========================
   PRODUCT / ORDER / PAYMENT
   ========================= */

-- 1. Schemas
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS warehouse;
CREATE SCHEMA IF NOT EXISTS ordering;
CREATE SCHEMA IF NOT EXISTS payment;

-- 2. Enum types (idempotent pattern)
DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_status') THEN
            CREATE TYPE order_status AS ENUM (
                'PENDING','WAIT_PAYMENT','PAID','PROCESSING','ALLOCATED','SHIPPED',
                'DELIVERED','COMPLETED','CANCELED','CLOSED'
                );
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
            CREATE TYPE payment_status AS ENUM (
                'INIT','PENDING','SUCCESS','FAILED','CLOSED','REFUNDED'
                );
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'refund_status') THEN
            CREATE TYPE refund_status AS ENUM (
                'REQUESTED','APPROVED','REJECTED','PROCESSING','COMPLETED','FAILED'
                );
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'inventory_movement_type') THEN
            CREATE TYPE inventory_movement_type AS ENUM (
                'INBOUND','OUTBOUND','RESERVE','RELEASE','ADJUST'
                );
        END IF;
    END
$$;

-- 3. Catalog: categories, brands, attributes
CREATE TABLE IF NOT EXISTS catalog.categories
(
    id         UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    parent_id  UUID        REFERENCES catalog.categories (id) ON DELETE SET NULL,
    name       TEXT        NOT NULL,
    path       TEXT        NOT NULL, -- e.g. /root/parent/child
    level      INT         NOT NULL DEFAULT 0,
    sort_order INT         NOT NULL DEFAULT 0,
    status     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (parent_id, name)
);

CREATE TABLE IF NOT EXISTS catalog.brands
(
    id         UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    name       TEXT        NOT NULL UNIQUE,
    logo_url   TEXT,
    country    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Attribute model (generic + options)
CREATE TABLE IF NOT EXISTS catalog.attributes
(
    id         UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    name       TEXT        NOT NULL,
    attr_type  TEXT        NOT NULL CHECK (attr_type IN ('basic', 'sale')), -- sale: contributes to SKU combination
    value_mode TEXT        NOT NULL CHECK (value_mode IN ('text', 'number', 'select', 'multi_select')),
    unit       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS catalog.attribute_options
(
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    attribute_id UUID NOT NULL REFERENCES catalog.attributes (id) ON DELETE CASCADE,
    value        TEXT NOT NULL,
    sort_order   INT  NOT NULL    DEFAULT 0,
    UNIQUE (attribute_id, value)
);

-- SPU (product) vs SKU
CREATE TABLE IF NOT EXISTS catalog.products
(
    id          UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    title       TEXT        NOT NULL,
    subtitle    TEXT,
    brand_id    UUID REFERENCES catalog.brands (id),
    category_id UUID REFERENCES catalog.categories (id),
    status      TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    main_image  TEXT,
    tags        TEXT[]               DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS catalog.product_skus
(
    id              UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    product_id      UUID        NOT NULL REFERENCES catalog.products (id) ON DELETE CASCADE,
    sku_code        TEXT UNIQUE,
    title           TEXT        NOT NULL,
    price_original  BIGINT      NOT NULL CHECK (price_original >= 0),
    price_sale      BIGINT      NOT NULL CHECK (price_sale >= 0),
    currency        CHAR(3)     NOT NULL DEFAULT 'CNY',
    weight_grams    INT,
    volume_cm3      INT,
    barcode         TEXT,
    status          TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    attributes_hash TEXT, -- optional hash for fast uniqueness checks
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, title)
);

-- SKU attribute values (denormalized snapshot for search)
CREATE TABLE IF NOT EXISTS catalog.sku_attribute_values
(
    sku_id              UUID NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE,
    attribute_id        UUID NOT NULL REFERENCES catalog.attributes (id) ON DELETE CASCADE,
    attribute_option_id UUID REFERENCES catalog.attribute_options (id) ON DELETE SET NULL,
    value_text          TEXT,
    PRIMARY KEY (sku_id, attribute_id)
);

-- Price history
CREATE TABLE IF NOT EXISTS catalog.price_history
(
    sku_id         UUID        NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE,
    valid_from     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    price_original BIGINT      NOT NULL CHECK (price_original >= 0),
    price_sale     BIGINT      NOT NULL CHECK (price_sale >= 0),
    PRIMARY KEY (sku_id, valid_from)
);

-- 4. Warehouse / Inventory
CREATE TABLE IF NOT EXISTS warehouse.warehouses
(
    id         UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    code       TEXT        NOT NULL UNIQUE,
    name       TEXT        NOT NULL,
    region     TEXT,
    address    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS warehouse.inventory
(
    warehouse_id       UUID NOT NULL REFERENCES warehouse.warehouses (id) ON DELETE CASCADE,
    sku_id             UUID NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE,
    quantity_available INT  NOT NULL DEFAULT 0 CHECK (quantity_available >= 0),
    quantity_reserved  INT  NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),
    PRIMARY KEY (warehouse_id, sku_id)
);

CREATE TABLE IF NOT EXISTS warehouse.inventory_movements
(
    id             UUID PRIMARY KEY                 DEFAULT uuid_generate_v7(),
    warehouse_id   UUID                    NOT NULL REFERENCES warehouse.warehouses (id) ON DELETE CASCADE,
    sku_id         UUID                    NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE,
    movement_type  inventory_movement_type NOT NULL,
    quantity       INT                     NOT NULL,
    reference_type TEXT, -- e.g. ORDER, REFUND, ADJUSTMENT
    reference_id   UUID,
    note           TEXT,
    created_at     TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    CHECK (quantity <> 0)
);

-- 5. Cart (optional)
CREATE TABLE IF NOT EXISTS ordering.carts
(
    id         UUID PRIMARY KEY     DEFAULT uuid_generate_v7(),
    user_id    UUID        REFERENCES security.users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ordering.cart_items
(
    cart_id        UUID        NOT NULL REFERENCES ordering.carts (id) ON DELETE CASCADE,
    sku_id         UUID        NOT NULL REFERENCES catalog.product_skus (id),
    quantity       INT         NOT NULL CHECK (quantity > 0),
    price_snapshot BIGINT      NOT NULL CHECK (price_snapshot >= 0),
    added_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (cart_id, sku_id)
);

-- 6. Orders
CREATE TABLE IF NOT EXISTS ordering.orders
(
    id               UUID PRIMARY KEY        DEFAULT uuid_generate_v7(),
    order_number     TEXT           NOT NULL UNIQUE,
    user_id          UUID REFERENCES security.users (id),
    status           order_status   NOT NULL DEFAULT 'PENDING',
    total_amount     BIGINT         NOT NULL CHECK (total_amount >= 0),
    discount_amount  BIGINT         NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
    pay_amount       BIGINT         NOT NULL CHECK (pay_amount >= 0),
    currency         CHAR(3)        NOT NULL DEFAULT 'CNY',
    payment_status   payment_status NOT NULL DEFAULT 'INIT',
    shipping_status  TEXT           NOT NULL DEFAULT 'PENDING' CHECK (shipping_status IN
                                                                      ('PENDING', 'ALLOCATED',
                                                                       'SHIPPED', 'DELIVERED')),
    address_snapshot JSONB, -- shipping address snapshot
    invoice_snapshot JSONB,
    remark           TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    paid_at          TIMESTAMPTZ,
    shipped_at       TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    canceled_at      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS ordering.order_items
(
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    order_id        UUID   NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE,
    product_id      UUID   NOT NULL REFERENCES catalog.products (id),
    sku_id          UUID   NOT NULL REFERENCES catalog.product_skus (id),
    sku_title       TEXT   NOT NULL,
    quantity        INT    NOT NULL CHECK (quantity > 0),
    price           BIGINT NOT NULL CHECK (price >= 0),        -- unit sale price
    discount_amount BIGINT NOT NULL  DEFAULT 0 CHECK (discount_amount >= 0),
    total_amount    BIGINT NOT NULL CHECK (total_amount >= 0), -- (price * qty - discount)
    sku_snapshot    JSONB,                                     -- attributes, images at purchase time
    UNIQUE (order_id, sku_id)
);

CREATE TABLE IF NOT EXISTS ordering.order_status_history
(
    order_id   UUID         NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE,
    status     order_status NOT NULL,
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    changed_by UUID REFERENCES security.users (id),
    note       TEXT,
    PRIMARY KEY (order_id, status, changed_at)
);

-- 7. Payment
CREATE TABLE IF NOT EXISTS payment.payments
(
    id               UUID PRIMARY KEY        DEFAULT uuid_generate_v7(),
    order_id         UUID           NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE,
    payment_sn       TEXT           NOT NULL UNIQUE,
    amount           BIGINT         NOT NULL CHECK (amount >= 0),
    currency         CHAR(3)        NOT NULL DEFAULT 'CNY',
    status           payment_status NOT NULL DEFAULT 'INIT',
    channel          TEXT           NOT NULL, -- e.g. ALIPAY, WECHAT, UNIONPAY
    request_payload  JSONB,
    response_payload JSONB,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    paid_at          TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payment.payment_transactions
(
    id              UUID PRIMARY KEY        DEFAULT uuid_generate_v7(),
    payment_id      UUID           NOT NULL REFERENCES payment.payments (id) ON DELETE CASCADE,
    external_txn_id TEXT,
    status          payment_status NOT NULL,
    raw_payload     JSONB,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (external_txn_id)
);

CREATE TABLE IF NOT EXISTS payment.refunds
(
    id           UUID PRIMARY KEY       DEFAULT uuid_generate_v7(),
    order_id     UUID          NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE,
    payment_id   UUID          REFERENCES payment.payments (id) ON DELETE SET NULL,
    refund_sn    TEXT          NOT NULL UNIQUE,
    amount       BIGINT        NOT NULL CHECK (amount >= 0),
    status       refund_status NOT NULL DEFAULT 'REQUESTED',
    reason       TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    approved_at  TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS payment.refund_items
(
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
    refund_id     UUID   NOT NULL REFERENCES payment.refunds (id) ON DELETE CASCADE,
    order_item_id UUID   NOT NULL REFERENCES ordering.order_items (id) ON DELETE CASCADE,
    quantity      INT    NOT NULL CHECK (quantity > 0),
    amount        BIGINT NOT NULL CHECK (amount >= 0),
    UNIQUE (refund_id, order_item_id)
);

-- 8. Indexes (representative; add more per workload)
CREATE INDEX IF NOT EXISTS idx_products_brand ON catalog.products (brand_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON catalog.products (category_id);
CREATE INDEX IF NOT EXISTS idx_sku_product ON catalog.product_skus (product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_sku ON warehouse.inventory (sku_id);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_sku ON warehouse.inventory_movements (sku_id);
CREATE INDEX IF NOT EXISTS idx_orders_user ON ordering.orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON ordering.orders (status);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON ordering.order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_order ON payment.payments (order_id);
CREATE INDEX IF NOT EXISTS idx_refunds_order ON payment.refunds (order_id);

-- 9. Triggers for updated_at using existing security.update_modified_column()
CREATE TRIGGER trg_categories_modtime
    BEFORE UPDATE
    ON catalog.categories
    FOR EACH ROW
EXECUTE FUNCTION security.update_modified_column();

CREATE TRIGGER trg_brands_modtime
    BEFORE UPDATE
    ON catalog.brands
    FOR EACH ROW
EXECUTE FUNCTION security.update_modified_column();

CREATE TRIGGER trg_products_modtime
    BEFORE UPDATE
    ON catalog.products
    FOR EACH ROW
EXECUTE FUNCTION security.update_modified_column();

CREATE TRIGGER trg_skus_modtime
    BEFORE UPDATE
    ON catalog.product_skus
    FOR EACH ROW
EXECUTE FUNCTION security.update_modified_column();

CREATE TRIGGER trg_orders_modtime
    BEFORE UPDATE
    ON ordering.orders
    FOR EACH ROW
EXECUTE FUNCTION security.update_modified_column();

CREATE TRIGGER trg_payments_modtime
    BEFORE UPDATE
    ON payment.payments
    FOR EACH ROW
EXECUTE FUNCTION security.update_modified_column();

-- 10. Derived view: order item aggregation (example)
CREATE OR REPLACE VIEW ordering.order_amounts AS
SELECT o.id,
       o.order_number,
       SUM(oi.total_amount) AS items_total,
       o.discount_amount,
       o.pay_amount
FROM ordering.orders o
         JOIN ordering.order_items oi ON o.id = oi.order_id
GROUP BY o.id, o.order_number, o.discount_amount, o.pay_amount;

-- 11. Basic check function (optional) to assert order totals = items
CREATE OR REPLACE FUNCTION ordering.validate_order_totals(p_order_id UUID)
    RETURNS BOOLEAN AS
$$
DECLARE
    v_items BIGINT;
    v_order RECORD;
BEGIN
    SELECT SUM(total_amount) INTO v_items FROM ordering.order_items WHERE order_id = p_order_id;
    SELECT total_amount, discount_amount, pay_amount
    INTO v_order
    FROM ordering.orders
    WHERE id = p_order_id;
    RETURN (v_items - v_order.discount_amount = v_order.pay_amount);
END;
$$ LANGUAGE plpgsql;

-- End new domain schemas
