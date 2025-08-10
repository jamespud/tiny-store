-- 扩展：启用UUID生成、密码加密和不区分大小写文本类型支持
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public; -- 生成UUID（v7带时间戳）
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public; -- 提供密码加密函数（如bcrypt）
CREATE EXTENSION IF NOT EXISTS CITEXT WITH SCHEMA public;
-- 支持不区分大小写的文本类型（CITEXT）

-- 创建安全相关的独立schema，隔离权限
CREATE SCHEMA IF NOT EXISTS security;
SET search_path TO security; -- 后续操作默认使用security schema

-- 通用触发器函数：自动更新记录的修改时间
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW(); -- 将更新时间设为当前时间
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 1. 用户表：存储系统用户核心信息，映射Spring Security的UserDetails
CREATE TABLE users
(
    id                      UUID PRIMARY KEY       DEFAULT public.uuid_generate_v4(),
    -- 用户登录名（不区分大小写，如"Admin"和"admin"视为同一用户）
    username                public.CITEXT NOT NULL UNIQUE,
    -- 存储bcrypt加密后的密码（非明文，长度固定60+）
    password                TEXT          NOT NULL,
    -- 用户邮箱（不区分大小写，唯一且格式验证）
    email                   public.CITEXT NOT NULL UNIQUE,
    -- 用户全名（如"张三"）
    full_name               TEXT,
    -- 账户是否启用（false时无法登录）
    enabled                 BOOLEAN       NOT NULL DEFAULT TRUE,
    -- 账户是否未过期（false时登录失败）
    account_non_expired     BOOLEAN       NOT NULL DEFAULT TRUE,
    -- 账户是否未锁定（false时登录失败，如多次输错密码后）
    account_non_locked      BOOLEAN       NOT NULL DEFAULT TRUE,
    -- 凭证（密码）是否未过期（false时需强制改密码）
    credentials_non_expired BOOLEAN       NOT NULL DEFAULT TRUE,
    -- 最后一次修改密码的时间（用于密码有效期校验）
    last_password_change_at TIMESTAMPTZ,
    -- 记录创建时间（自动生成，不可修改）
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    -- 记录最后更新时间（由触发器自动维护）
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    -- 约束：验证邮箱格式（符合标准邮箱规则）
    CONSTRAINT valid_email CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    -- 约束：确保密码是有效的bcrypt哈希（长度≥60）
    CONSTRAINT chk_users_password_min_length CHECK (length(password) >= 60)
);

-- 触发器：自动更新users表的updated_at字段
CREATE TRIGGER update_user_modtime
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 2. 角色表：定义系统角色（如管理员、普通用户）
CREATE TABLE roles
(
    id          UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),
    -- 角色名称（唯一，建议前缀ROLE_，如ROLE_ADMIN）
    name        TEXT        NOT NULL UNIQUE,
    -- 角色描述（如"系统管理员，拥有所有权限"）
    description TEXT,
    -- 角色创建时间
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER update_role_modtime
    BEFORE UPDATE
    ON roles
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 3. 用户角色关联表：实现用户与角色的多对多关系
CREATE TABLE user_roles
(
    -- 关联的用户ID
    user_id     UUID        NOT NULL,
    -- 关联的角色ID
    role_id     UUID        NOT NULL,
    -- 角色分配时间
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 分配角色的操作员ID（关联users表，可为NULL表示系统分配）
    assigned_by UUID REFERENCES users (id),
    PRIMARY KEY (user_id, role_id), -- 复合主键确保用户-角色关系唯一
    -- 外键：用户删除时，关联关系自动删除
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- 外键：角色删除时，关联关系自动删除
    FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

-- 4. 权限表：定义细粒度权限（如"用户查看"、"订单修改"）
CREATE TABLE permissions
(
    id          UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),
    -- 权限名称（唯一，建议格式"资源:操作"，如user:read、order:write）
    name        TEXT        NOT NULL UNIQUE,
    -- 权限描述（如"允许查看用户信息"）
    description TEXT,
    -- 权限创建时间
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER update_permission_modtime
    BEFORE UPDATE
    ON permissions
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 5. 角色权限关联表：实现角色与权限的多对多关系
CREATE TABLE role_permissions
(
    -- 关联的角色ID
    role_id       UUID        NOT NULL,
    -- 关联的权限ID
    permission_id UUID        NOT NULL,
    PRIMARY KEY (role_id, permission_id), -- 复合主键确保角色-权限关系唯一
    -- 外键：角色删除时，关联关系自动删除
    FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    -- 外键：权限删除时，关联关系自动删除
    FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE,
    -- 权限分配时间
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER update_role_permission_modtime
    BEFORE UPDATE
    ON role_permissions
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 6. OAuth2客户端表：存储Spring Authorization Server的客户端配置
CREATE TABLE oauth2_registered_client
(
    id                            UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),
    -- 客户端唯一标识（如"web-client"）
    client_id                     TEXT        NOT NULL UNIQUE,
    -- client_id的创建时间
    client_id_issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 客户端密钥（加密存储，公开客户端可为NULL）
    client_secret                 TEXT,
    -- client_secret的过期时间（NULL表示永不过期）
    client_secret_expires_at      TIMESTAMPTZ,
    -- 客户端名称（如"Web管理端"）
    client_name                   TEXT        NOT NULL,
    -- 客户端支持的认证方法（JSON数组，如["client_secret_basic", "client_secret_post"]）
    client_authentication_methods JSONB       NOT NULL DEFAULT '[]'::jsonb,
    -- 客户端支持的授权类型（JSON数组，如["authorization_code", "refresh_token"]）
    authorization_grant_types     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    -- 授权成功后的重定向URI（JSON数组）
    redirect_uris                 JSONB                DEFAULT '[]'::jsonb,
    -- 登出后的重定向URI（JSON数组）
    post_logout_redirect_uris     JSONB                DEFAULT '[]'::jsonb,
    -- 客户端请求的权限范围（JSON数组，如["user.read", "order.write"]）
    scopes                        JSONB       NOT NULL DEFAULT '[]'::jsonb,
    -- 客户端配置（JSON，如token超时、是否需要确认等）
    client_settings               JSONB       NOT NULL DEFAULT '{}'::jsonb,
    -- 令牌配置（JSON，如access_token有效期、refresh_token策略等）
    token_settings                JSONB       NOT NULL DEFAULT '{}'::jsonb,
    -- 记录创建时间
    created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 记录最后更新时间（由触发器自动维护）
    updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 约束：确保认证方法是JSON数组
    CONSTRAINT chk_oauth2_client_auth_methods_array CHECK (jsonb_typeof(client_authentication_methods) = 'array'),
    -- 约束：确保授权类型是JSON数组
    CONSTRAINT chk_oauth2_client_grant_types_array CHECK (jsonb_typeof(authorization_grant_types) = 'array'),
    -- 约束：确保重定向URI是JSON数组（允许为NULL）
    CONSTRAINT chk_oauth2_client_redirect_uris_array CHECK (redirect_uris IS NULL OR
                                                            jsonb_typeof(redirect_uris) = 'array'),
    -- 约束：确保登出重定向URI是JSON数组（允许为NULL）
    CONSTRAINT chk_oauth2_client_post_logout_uris_array CHECK (post_logout_redirect_uris IS NULL OR
                                                               jsonb_typeof(post_logout_redirect_uris) =
                                                               'array'),
    -- 约束：确保权限范围是JSON数组
    CONSTRAINT chk_oauth2_client_scopes_array CHECK (jsonb_typeof(scopes) = 'array')
);

-- 触发器：自动更新oauth2_registered_client表的updated_at字段
CREATE TRIGGER update_oauth2_client_modtime
    BEFORE UPDATE
    ON oauth2_registered_client
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 7. OAuth2授权表：存储用户对客户端的授权记录（含令牌信息）
CREATE TABLE oauth2_authorization
(
    id                            UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    -- 关联的客户端ID（外键）
    registered_client_id          UUID NOT NULL,
    -- 授权主体名称（通常是用户名或用户ID）
    principal_name                TEXT NOT NULL,
    -- 授权类型（如"authorization_code"、"client_credentials"）
    authorization_grant_type      TEXT NOT NULL,
    -- 已授权的权限范围（JSON数组）
    authorized_scopes             JSONB,
    -- 授权上下文属性（JSON，如请求参数、用户信息等）
    attributes                    JSONB,
    -- 授权状态标识（用于防CSRF，如OAuth2的state参数）
    state                         TEXT,
    -- 授权码值（一次性使用，加密存储）
    authorization_code_value      TEXT,
    -- 授权码创建时间
    authorization_code_issued_at  TIMESTAMPTZ,
    -- 授权码过期时间
    authorization_code_expires_at TIMESTAMPTZ,
    -- 授权码元数据（JSON，如绑定的客户端、用户信息等）
    authorization_code_metadata   JSONB,
    -- 访问令牌值（加密存储，建议存哈希）
    access_token_value            TEXT,
    -- 访问令牌创建时间
    access_token_issued_at        TIMESTAMPTZ,
    -- 访问令牌过期时间
    access_token_expires_at       TIMESTAMPTZ,
    -- 访问令牌元数据（JSON，如令牌类型、JWT声明等）
    access_token_metadata         JSONB,
    -- 访问令牌类型（如"bearer"）
    access_token_type             TEXT,
    -- 访问令牌包含的权限范围（JSON数组）
    access_token_scopes           JSONB,
    -- OIDC ID令牌值（加密存储，建议存哈希）
    oidc_id_token_value           TEXT,
    -- ID令牌创建时间
    oidc_id_token_issued_at       TIMESTAMPTZ,
    -- ID令牌过期时间
    oidc_id_token_expires_at      TIMESTAMPTZ,
    -- ID令牌元数据（JSON，如签名算法、声明等）
    oidc_id_token_metadata        JSONB,
    -- 刷新令牌值（加密存储，建议存哈希）
    refresh_token_value           TEXT,
    -- 刷新令牌创建时间
    refresh_token_issued_at       TIMESTAMPTZ,
    -- 刷新令牌过期时间
    refresh_token_expires_at      TIMESTAMPTZ,
    -- 刷新令牌元数据（JSON，如关联的访问令牌等）
    refresh_token_metadata        JSONB,
    -- 用户码值（设备授权流程使用）
    user_code_value               TEXT,
    -- 用户码创建时间
    user_code_issued_at           TIMESTAMPTZ,
    -- 用户码过期时间
    user_code_expires_at          TIMESTAMPTZ,
    -- 用户码元数据（JSON）
    user_code_metadata            JSONB,
    -- 设备码值（设备授权流程使用）
    device_code_value             TEXT,
    -- 设备码创建时间
    device_code_issued_at         TIMESTAMPTZ,
    -- 设备码过期时间
    device_code_expires_at        TIMESTAMPTZ,
    -- 设备码元数据（JSON）
    device_code_metadata          JSONB,
    -- 外键：客户端删除时，关联的授权记录自动删除
    FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client (id) ON DELETE CASCADE,
    -- 约束：确保state唯一（防重复提交）
    CONSTRAINT uk_authorization_state UNIQUE (state)
);

-- 8. OAuth2授权确认表：记录用户对客户端权限的确认状态
CREATE TABLE oauth2_authorization_consent
(
    -- 关联的客户端ID
    registered_client_id UUID        NOT NULL,
    -- 授权主体名称（用户名或用户ID）
    principal_name       TEXT        NOT NULL,
    -- 已确认的权限列表（JSON数组，如["user.read", "order.write"]）
    authorities          JSONB       NOT NULL,
    -- 记录创建时间
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 记录最后更新时间（由触发器自动维护）
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (registered_client_id, principal_name), -- 复合主键：客户端+用户的权限确认唯一
    -- 外键：关联客户端表
    FOREIGN KEY (registered_client_id) REFERENCES oauth2_registered_client (id)
);

-- 触发器：自动更新oauth2_authorization_consent表的updated_at字段
CREATE TRIGGER update_oauth2_consent_modtime
    BEFORE UPDATE
    ON oauth2_authorization_consent
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- 9. 用户登录历史表：记录用户登录行为，用于审计和安全分析
CREATE TABLE user_login_history
(
    id             UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),
    -- 关联的用户ID
    user_id        UUID        NOT NULL,
    -- 登录时间
    login_time     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 登出时间（NULL表示未登出）
    logout_time    TIMESTAMPTZ,
    -- 登录IP地址（PostgreSQL原生inet类型，支持IP范围查询）
    ip_address     INET,
    -- 登录客户端的User-Agent信息（如浏览器、设备型号）
    user_agent     TEXT,
    -- 登录是否成功（true=成功，false=失败）
    login_success  BOOLEAN     NOT NULL,
    -- 登录失败原因（如密码错误、账户锁定，成功时为NULL）
    failure_reason TEXT,
    -- 登录会话ID（关联应用层会话管理）
    session_id     TEXT,
    -- 外键：关联用户表
    FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 10. 用户密码历史表：记录用户密码变更历史，防止密码复用
CREATE TABLE user_password_history
(
    id            UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),
    -- 关联的用户ID
    user_id       UUID        NOT NULL,
    -- 历史密码哈希（bcrypt加密后的值）
    password_hash TEXT        NOT NULL,
    -- 密码创建时间（即该密码被设置的时间）
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 外键：用户删除时，历史密码自动删除
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TRIGGER update_user_password_history_modtime
    BEFORE UPDATE
    ON user_password_history
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

-- === 索引：优化查询性能 ===
-- 用户角色关联表：按用户ID查询
CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);
-- 用户角色关联表：按角色ID查询
CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);
-- 角色权限关联表：按角色ID查询
CREATE INDEX idx_role_permissions_role_id ON role_permissions (role_id);
-- 角色权限关联表：按权限ID查询
CREATE INDEX idx_role_permissions_permission_id ON role_permissions (permission_id);
-- OAuth2授权表：按客户端ID查询
CREATE INDEX idx_oauth2_authorization_client_id ON oauth2_authorization (registered_client_id);
-- OAuth2授权表：按用户（principal_name）查询
CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization (principal_name);
-- OAuth2授权表：按访问令牌查询（仅非NULL值）
CREATE INDEX idx_oauth2_authorization_access_token ON oauth2_authorization (access_token_value) WHERE access_token_value IS NOT NULL;
-- OAuth2授权表：按刷新令牌查询（仅非NULL值）
CREATE INDEX idx_oauth2_authorization_refresh_token ON oauth2_authorization (refresh_token_value) WHERE refresh_token_value IS NOT NULL;
-- OAuth2授权表：按访问令牌过期时间查询（用于清理过期令牌）
CREATE INDEX idx_oauth2_authorization_access_token_expires_at ON oauth2_authorization (access_token_expires_at);
-- OAuth2授权表：按刷新令牌过期时间查询（用于清理过期令牌）
CREATE INDEX idx_oauth2_authorization_refresh_token_expires_at ON oauth2_authorization (refresh_token_expires_at);
-- 用户登录历史表：按用户ID查询
CREATE INDEX idx_user_login_history_user_id ON user_login_history (user_id);
-- 用户登录历史表：按登录时间查询（用于时间范围统计）
CREATE INDEX idx_user_login_history_login_time ON user_login_history (login_time);
-- 用户登录历史表：按IP地址查询（用于IP分析）
CREATE INDEX idx_user_login_history_ip ON user_login_history (ip_address);
-- 用户密码历史表：按用户ID查询（用于密码复用检查）
CREATE INDEX idx_user_password_history_user_id ON user_password_history (user_id);

-- === 视图：简化权限查询 ===
-- 视图1：用户拥有的所有权限（明细）
CREATE OR REPLACE VIEW user_effective_permissions AS
SELECT DISTINCT u.id   AS user_id,
                u.username,
                p.id   AS permission_id,
                p.name AS permission_name
FROM users u
         JOIN user_roles ur ON u.id = ur.user_id
         JOIN roles r ON ur.role_id = r.id
         JOIN role_permissions rp ON r.id = rp.role_id
         JOIN permissions p ON rp.permission_id = p.id;

-- 视图2：用户拥有的权限（JSON聚合，便于应用层解析）
CREATE OR REPLACE VIEW user_permissions_agg AS
SELECT u.id                                       AS user_id,
       u.username,
       jsonb_agg(DISTINCT p.name ORDER BY p.name) AS permissions -- 聚合为权限数组
FROM users u
         LEFT JOIN user_roles ur ON u.id = ur.user_id
         LEFT JOIN roles r ON ur.role_id = r.id
         LEFT JOIN role_permissions rp ON r.id = rp.role_id
         LEFT JOIN permissions p ON rp.permission_id = p.id
GROUP BY u.id, u.username;

-- === 触发器函数：密码变更管理 ===
-- 功能：防止密码复用（限制最近5次）、自动记录历史、更新修改时间
CREATE OR REPLACE FUNCTION enforce_and_track_password_change()
    RETURNS TRIGGER AS
$$
DECLARE
    reuse_count           INT;
    password_reuse_window INT := 5; -- 禁止复用最近5次密码（可调整）
BEGIN
    -- 仅在密码被修改时触发逻辑
    IF TG_OP = 'UPDATE' AND NEW.password IS DISTINCT FROM OLD.password THEN
        -- 检查新密码是否在最近N次历史中出现
        SELECT COUNT(*)
        INTO reuse_count
        FROM (SELECT password_hash
              FROM user_password_history
              WHERE user_id = OLD.id
              ORDER BY created_at DESC
              LIMIT password_reuse_window) recent
        WHERE recent.password_hash = NEW.password;

        -- 若存在复用，抛出异常阻止更新
        IF reuse_count > 0 THEN
            RAISE EXCEPTION 'Password was used in the last % times', password_reuse_window;
        END IF;

        -- 记录新密码到历史表
        INSERT INTO user_password_history (user_id, password_hash)
        VALUES (OLD.id, NEW.password);

        -- 更新密码最后修改时间
        NEW.last_password_change_at = NOW();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 触发器：绑定密码变更管理函数到users表
CREATE TRIGGER trg_users_password_change
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION enforce_and_track_password_change();

-- === 工具函数：清理过期授权记录 ===
-- 功能：删除已过期的授权码、访问令牌、刷新令牌记录
CREATE OR REPLACE FUNCTION purge_expired_authorizations()
    RETURNS INTEGER AS
$$
DECLARE
    v_count INT; -- 记录删除的行数
BEGIN
    DELETE
    FROM oauth2_authorization
    WHERE (access_token_expires_at IS NOT NULL AND access_token_expires_at < NOW())
       OR (refresh_token_expires_at IS NOT NULL AND refresh_token_expires_at < NOW())
       OR (authorization_code_expires_at IS NOT NULL AND authorization_code_expires_at < NOW());
    GET DIAGNOSTICS v_count = ROW_COUNT; -- 获取删除行数
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- === 初始数据：默认角色和权限 ===
-- 初始角色（管理员、普通用户）
INSERT INTO roles (name, description)
VALUES ('ROLE_ADMIN', '系统管理员，拥有所有权限'),
       ('ROLE_USER', '普通用户，拥有基础操作权限')
ON CONFLICT (name) DO NOTHING;
-- 冲突时不插入（幂等性）

-- 初始权限（用户管理、角色管理相关）
INSERT INTO permissions (name, description)
VALUES ('user:read', '查看用户信息'),
       ('user:write', '创建或修改用户'),
       ('user:delete', '删除用户'),
       ('role:read', '查看角色信息'),
       ('role:write', '创建或修改角色')
ON CONFLICT (name) DO NOTHING;
-- 冲突时不插入（幂等性）

-- 1. 扩展与兼容性处理
-- 优先创建uuid-ossp扩展（提供UUID生成函数）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

-- 2. 创建业务schema
CREATE SCHEMA IF NOT EXISTS catalog; -- 商品目录服务
CREATE SCHEMA IF NOT EXISTS warehouse; -- 仓储服务
CREATE SCHEMA IF NOT EXISTS ordering; -- 订单服务
CREATE SCHEMA IF NOT EXISTS payment; -- 支付服务
CREATE SCHEMA IF NOT EXISTS security;
-- 安全服务（用户相关）

-- 3. 枚举类型（确保幂等性创建）
DO
$$
    BEGIN
        -- 订单状态枚举
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'order_status') THEN
            CREATE TYPE order_status AS ENUM (
                'PENDING','WAIT_PAYMENT','PAID','PROCESSING','ALLOCATED','SHIPPED',
                'DELIVERED','COMPLETED','CANCELED','CLOSED'
                );
        END IF;
        -- 支付状态枚举
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
            CREATE TYPE payment_status AS ENUM (
                'INIT','PENDING','SUCCESS','FAILED','CLOSED','REFUNDED'
                );
        END IF;
        -- 退款状态枚举
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'refund_status') THEN
            CREATE TYPE refund_status AS ENUM (
                'REQUESTED','APPROVED','REJECTED','PROCESSING','COMPLETED','FAILED'
                );
        END IF;
        -- 库存变动类型枚举
        IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'inventory_movement_type') THEN
            CREATE TYPE inventory_movement_type AS ENUM (
                'INBOUND','OUTBOUND','RESERVE','RELEASE','ADJUST'
                );
        END IF;
    END
$$;

-- 4. 通用触发器函数（自动更新updated_at）
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 5. 商品目录服务表（catalog）
CREATE TABLE IF NOT EXISTS catalog.categories
(
    id         UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),            -- 统一UUID
    parent_id  UUID        REFERENCES catalog.categories (id) ON DELETE SET NULL, -- 自关联UUID
    name       TEXT        NOT NULL,
    path       TEXT        NOT NULL,                                              -- 分类路径（如/家电/冰箱）
    level      INT         NOT NULL DEFAULT 0,                                    -- 分类级别（1-3级）
    sort_order INT         NOT NULL DEFAULT 0,                                    -- 排序权重
    status     BOOLEAN     NOT NULL DEFAULT TRUE,                                 -- 是否启用
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (parent_id, name)
);

CREATE TABLE IF NOT EXISTS catalog.brands
(
    id         UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),
    name       TEXT        NOT NULL UNIQUE, -- 品牌名称（唯一）
    logo_url   TEXT,                        -- 品牌LOGO
    country    TEXT,                        -- 品牌所属国家
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS catalog.attributes
(
    id         UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),
    name       TEXT        NOT NULL,                                                                    -- 属性名称（如"颜色"、"内存"）
    attr_type  TEXT        NOT NULL CHECK (attr_type IN ('basic', 'sale')),                             -- basic:普通属性; sale:影响SKU的属性
    value_mode TEXT        NOT NULL CHECK (value_mode IN ('text', 'number', 'select', 'multi_select')), -- 属性值类型
    unit       TEXT,                                                                                    -- 单位（如"GB"、"cm"）
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS catalog.attribute_options
(
    id           UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),
    attribute_id UUID NOT NULL REFERENCES catalog.attributes (id) ON DELETE CASCADE, -- 关联属性
    value        TEXT NOT NULL,                                                      -- 选项值（如"红色"、"8GB"）
    sort_order   INT  NOT NULL    DEFAULT 0,                                         -- 排序
    UNIQUE (attribute_id, value)
);

CREATE TABLE IF NOT EXISTS catalog.products
(
    id          UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),                                 -- SPU ID
    title       TEXT        NOT NULL,                                                                   -- 商品名称（如"iPhone 15"）
    subtitle    TEXT,                                                                                   -- 副标题（如"6.1英寸显示屏"）
    brand_id    UUID REFERENCES catalog.brands (id),                                                    -- 关联品牌
    category_id UUID REFERENCES catalog.categories (id),                                                -- 关联分类
    status      TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE')), -- 商品状态
    main_image  TEXT,                                                                                   -- 主图URL
    tags        TEXT[]               DEFAULT '{}',                                                      -- 标签（如"新品"、"促销"）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS catalog.product_skus
(
    id              UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),                                    -- SKU ID
    product_id      UUID        NOT NULL REFERENCES catalog.products (id) ON DELETE CASCADE,                   -- 关联SPU
    sku_code        TEXT UNIQUE,                                                                               -- SKU编码（业务唯一标识）
    title           TEXT        NOT NULL,                                                                      -- SKU名称（如"iPhone 15-128GB-黑色"）
    price_original  BIGINT      NOT NULL CHECK (price_original >= 0),                                          -- 原价（分）
    price_sale      BIGINT      NOT NULL CHECK (price_sale >= 0),                                              -- 售价（分）
    currency        CHAR(3)     NOT NULL DEFAULT 'CNY',                                                        -- 货币单位
    weight_grams    INT,                                                                                       -- 重量（克）
    volume_cm3      INT,                                                                                       -- 体积（立方厘米）
    barcode         TEXT,                                                                                      -- 条形码
    status          TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')), -- SKU状态
    attributes_hash TEXT,                                                                                      -- 属性哈希（用于快速查重）
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, title)
);

CREATE TABLE IF NOT EXISTS catalog.sku_attribute_values
(
    sku_id              UUID NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE, -- 关联SKU
    attribute_id        UUID NOT NULL REFERENCES catalog.attributes (id) ON DELETE CASCADE,   -- 关联属性
    attribute_option_id UUID REFERENCES catalog.attribute_options (id) ON DELETE SET NULL,    -- 关联属性选项（可选）
    value_text          TEXT,                                                                 -- 文本值（当属性值不是选项时）
    PRIMARY KEY (sku_id, attribute_id)
);

CREATE TABLE IF NOT EXISTS catalog.price_history
(
    sku_id         UUID        NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE, -- 关联SKU
    valid_from     TIMESTAMPTZ NOT NULL DEFAULT NOW(),                                          -- 生效时间
    price_original BIGINT      NOT NULL CHECK (price_original >= 0),                            -- 原价（分）
    price_sale     BIGINT      NOT NULL CHECK (price_sale >= 0),                                -- 售价（分）
    PRIMARY KEY (sku_id, valid_from)
);

-- 6. 仓储服务表（warehouse）
CREATE TABLE IF NOT EXISTS warehouse.warehouses
(
    id         UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(), -- 仓库ID
    code       TEXT        NOT NULL UNIQUE,                            -- 仓库编码（如"WH-BJ-001"）
    name       TEXT        NOT NULL,                                   -- 仓库名称（如"北京仓"）
    region     TEXT,                                                   -- 所属区域（如"华北"）
    address    TEXT,                                                   -- 仓库地址
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS warehouse.inventory
(
    warehouse_id       UUID NOT NULL REFERENCES warehouse.warehouses (id) ON DELETE CASCADE, -- 关联仓库
    sku_id             UUID NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE, -- 关联SKU
    quantity_available INT  NOT NULL DEFAULT 0 CHECK (quantity_available >= 0),              -- 可用库存
    quantity_reserved  INT  NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0),               -- 已预留库存（订单占用）
    PRIMARY KEY (warehouse_id, sku_id)
);

CREATE TABLE IF NOT EXISTS warehouse.inventory_movements
(
    id             UUID PRIMARY KEY                 DEFAULT public.uuid_generate_v4(),                      -- 库存变动ID
    warehouse_id   UUID                    NOT NULL REFERENCES warehouse.warehouses (id) ON DELETE CASCADE, -- 关联仓库
    sku_id         UUID                    NOT NULL REFERENCES catalog.product_skus (id) ON DELETE CASCADE, -- 关联SKU
    movement_type  inventory_movement_type NOT NULL,                                                        -- 变动类型（入库/出库等）
    quantity       INT                     NOT NULL,                                                        -- 变动数量（正数增加，负数减少）
    reference_type TEXT,                                                                                    -- 关联业务类型（如"ORDER"、"REFUND"）
    reference_id   UUID,                                                                                    -- 关联业务ID（如订单ID）
    note           TEXT,                                                                                    -- 备注
    created_at     TIMESTAMPTZ             NOT NULL DEFAULT NOW()
        CHECK (quantity <> 0)                                                                               -- 确保数量非零
);

-- 7. 订单服务表（ordering）
CREATE TABLE IF NOT EXISTS ordering.carts
(
    id         UUID PRIMARY KEY     DEFAULT public.uuid_generate_v4(),        -- 购物车ID
    user_id    UUID        REFERENCES security.users (id) ON DELETE SET NULL, -- 关联用户（匿名购物车可为NULL）
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ordering.cart_items
(
    cart_id        UUID        NOT NULL REFERENCES ordering.carts (id) ON DELETE CASCADE, -- 关联购物车
    sku_id         UUID        NOT NULL REFERENCES catalog.product_skus (id),             -- 关联SKU
    quantity       INT         NOT NULL CHECK (quantity > 0),                             -- 数量
    price_snapshot BIGINT      NOT NULL CHECK (price_snapshot >= 0),                      -- 价格快照（分）
    added_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),                                    -- 添加时间
    PRIMARY KEY (cart_id, sku_id)
);

CREATE TABLE IF NOT EXISTS ordering.orders
(
    id               UUID PRIMARY KEY        DEFAULT public.uuid_generate_v4(),       -- 订单ID
    order_number     TEXT           NOT NULL UNIQUE,                                  -- 订单编号（业务唯一，如"JD20240901001"）
    user_id          UUID REFERENCES security.users (id),                             -- 关联用户
    status           order_status   NOT NULL DEFAULT 'PENDING',                       -- 订单状态
    total_amount     BIGINT         NOT NULL CHECK (total_amount >= 0),               -- 总金额（分）
    discount_amount  BIGINT         NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),  -- 优惠金额（分）
    pay_amount       BIGINT         NOT NULL CHECK (pay_amount >= 0),                 -- 实付金额（分）
    currency         CHAR(3)        NOT NULL DEFAULT 'CNY',                           -- 货币单位
    payment_status   payment_status NOT NULL DEFAULT 'INIT',                          -- 支付状态
    shipping_status  TEXT           NOT NULL DEFAULT 'PENDING' CHECK (shipping_status IN
                                                                      ('PENDING', 'ALLOCATED',
                                                                       'SHIPPED',
                                                                       'DELIVERED')), -- 物流状态
    address_snapshot JSONB,                                                           -- 收货地址快照（下单时保存，避免地址变更影响订单）
    invoice_snapshot JSONB,                                                           -- 发票信息快照
    remark           TEXT,                                                            -- 订单备注
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    paid_at          TIMESTAMPTZ,                                                     -- 支付时间
    shipped_at       TIMESTAMPTZ,                                                     -- 发货时间
    completed_at     TIMESTAMPTZ,                                                     -- 完成时间
    canceled_at      TIMESTAMPTZ                                                      -- 取消时间
);

CREATE TABLE IF NOT EXISTS ordering.order_items
(
    id              UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),                -- 订单项ID
    order_id        UUID   NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE, -- 关联订单
    product_id      UUID   NOT NULL REFERENCES catalog.products (id),                  -- 关联SPU
    sku_id          UUID   NOT NULL REFERENCES catalog.product_skus (id),              -- 关联SKU
    sku_title       TEXT   NOT NULL,                                                   -- SKU名称快照
    quantity        INT    NOT NULL CHECK (quantity > 0),                              -- 数量
    price           BIGINT NOT NULL CHECK (price >= 0),                                -- 单价（分）
    discount_amount BIGINT NOT NULL  DEFAULT 0 CHECK (discount_amount >= 0),           -- 优惠金额（分）
    total_amount    BIGINT NOT NULL CHECK (total_amount >= 0),                         -- 小计金额（分）
    sku_snapshot    JSONB,                                                             -- SKU属性快照（下单时保存）
    UNIQUE (order_id, sku_id)
);

CREATE TABLE IF NOT EXISTS ordering.order_status_history
(
    order_id   UUID         NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE, -- 关联订单
    status     order_status NOT NULL,                                                   -- 状态
    changed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),                                     -- 变更时间
    changed_by UUID REFERENCES security.users (id),                                     -- 操作人
    note       TEXT,                                                                    -- 备注
    PRIMARY KEY (order_id, status, changed_at)
);

-- 8. 支付服务表（payment）
CREATE TABLE IF NOT EXISTS payment.payments
(
    id               UUID PRIMARY KEY        DEFAULT public.uuid_generate_v4(),                 -- 支付记录ID
    order_id         UUID           NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE, -- 关联订单
    payment_sn       TEXT           NOT NULL UNIQUE,                                            -- 支付单号（业务唯一）
    amount           BIGINT         NOT NULL CHECK (amount >= 0),                               -- 支付金额（分）
    currency         CHAR(3)        NOT NULL DEFAULT 'CNY',                                     -- 货币单位
    status           payment_status NOT NULL DEFAULT 'INIT',                                    -- 支付状态
    channel          TEXT           NOT NULL,                                                   -- 支付渠道（如"ALIPAY"、"WECHAT"）
    request_payload  JSONB,                                                                     -- 支付请求参数
    response_payload JSONB,                                                                     -- 支付响应结果
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    paid_at          TIMESTAMPTZ                                                                -- 支付成功时间
);

CREATE TABLE IF NOT EXISTS payment.payment_transactions
(
    id              UUID PRIMARY KEY        DEFAULT public.uuid_generate_v4(),                  -- 支付交易ID
    payment_id      UUID           NOT NULL REFERENCES payment.payments (id) ON DELETE CASCADE, -- 关联支付记录
    external_txn_id TEXT,                                                                       -- 外部交易号（如支付宝交易号）
    status          payment_status NOT NULL,                                                    -- 交易状态
    raw_payload     JSONB,                                                                      -- 原始支付数据
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (external_txn_id)
);

CREATE TABLE IF NOT EXISTS payment.refunds
(
    id           UUID PRIMARY KEY       DEFAULT public.uuid_generate_v4(),                 -- 退款记录ID
    order_id     UUID          NOT NULL REFERENCES ordering.orders (id) ON DELETE CASCADE, -- 关联订单
    payment_id   UUID          REFERENCES payment.payments (id) ON DELETE SET NULL,        -- 关联支付记录
    refund_sn    TEXT          NOT NULL UNIQUE,                                            -- 退款单号（业务唯一）
    amount       BIGINT        NOT NULL CHECK (amount >= 0),                               -- 退款金额（分）
    status       refund_status NOT NULL DEFAULT 'REQUESTED',                               -- 退款状态
    reason       TEXT,                                                                     -- 退款原因
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    approved_at  TIMESTAMPTZ,                                                              -- 审核通过时间
    completed_at TIMESTAMPTZ                                                               -- 退款完成时间
);

CREATE TABLE IF NOT EXISTS payment.refund_items
(
    id            UUID PRIMARY KEY DEFAULT public.uuid_generate_v4(),                     -- 退款项ID
    refund_id     UUID   NOT NULL REFERENCES payment.refunds (id) ON DELETE CASCADE,      -- 关联退款记录
    order_item_id UUID   NOT NULL REFERENCES ordering.order_items (id) ON DELETE CASCADE, -- 关联订单项
    quantity      INT    NOT NULL CHECK (quantity > 0),                                   -- 退款数量
    amount        BIGINT NOT NULL CHECK (amount >= 0),                                    -- 退款金额（分）
    UNIQUE (refund_id, order_item_id)
);

-- 9. 索引优化（提升查询性能）
CREATE INDEX IF NOT EXISTS idx_categories_parent ON catalog.categories (parent_id);
CREATE INDEX IF NOT EXISTS idx_products_brand ON catalog.products (brand_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON catalog.products (category_id);
CREATE INDEX IF NOT EXISTS idx_sku_product ON catalog.product_skus (product_id);
CREATE INDEX IF NOT EXISTS idx_inventory_sku ON warehouse.inventory (sku_id);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_sku ON warehouse.inventory_movements (sku_id);
CREATE INDEX IF NOT EXISTS idx_inventory_movements_reference ON warehouse.inventory_movements (reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_carts_user ON ordering.carts (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_user ON ordering.orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON ordering.orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON ordering.orders (created_at);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON ordering.order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_order ON payment.payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_status ON payment.payments (status);
CREATE INDEX IF NOT EXISTS idx_refunds_order ON payment.refunds (order_id);

-- 10. 自动更新updated_at的触发器
CREATE TRIGGER trg_categories_modtime
    BEFORE UPDATE
    ON catalog.categories
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_brands_modtime
    BEFORE UPDATE
    ON catalog.brands
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_attributes_modtime
    BEFORE UPDATE
    ON catalog.attributes
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_products_modtime
    BEFORE UPDATE
    ON catalog.products
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_skus_modtime
    BEFORE UPDATE
    ON catalog.product_skus
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_warehouses_modtime
    BEFORE UPDATE
    ON warehouse.warehouses
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_carts_modtime
    BEFORE UPDATE
    ON ordering.carts
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_orders_modtime
    BEFORE UPDATE
    ON ordering.orders
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER trg_payments_modtime
    BEFORE UPDATE
    ON payment.payments
    FOR EACH ROW
EXECUTE FUNCTION update_modified_column();



-- 11. 辅助视图与函数
-- 订单金额校验视图（确保订单总金额与订单项总和一致）
CREATE OR REPLACE VIEW ordering.order_amounts AS
SELECT o.id,
       o.order_number,
       SUM(oi.total_amount)                                      AS items_total, -- 订单项总金额
       o.discount_amount,
       o.pay_amount,
       -- 校验：订单项总和 - 优惠金额 是否等于实付金额
       (SUM(oi.total_amount) - o.discount_amount = o.pay_amount) AS is_valid
FROM ordering.orders o
         JOIN ordering.order_items oi ON o.id = oi.order_id
GROUP BY o.id, o.order_number, o.discount_amount, o.pay_amount;

-- 订单金额校验函数
CREATE OR REPLACE FUNCTION ordering.validate_order_totals(p_order_id UUID)
    RETURNS BOOLEAN AS
$$
DECLARE
    v_items_total BIGINT;
    v_order       RECORD;
BEGIN
    SELECT SUM(total_amount)
    INTO v_items_total
    FROM ordering.order_items
    WHERE order_id = p_order_id;

    SELECT total_amount, discount_amount, pay_amount
    INTO v_order
    FROM ordering.orders
    WHERE id = p_order_id;

    RETURN (v_items_total - v_order.discount_amount = v_order.pay_amount);
END;
$$ LANGUAGE plpgsql;
