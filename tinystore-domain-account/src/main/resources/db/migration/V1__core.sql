-- 公共表

-- 1. 核心用户主表 user_core
CREATE TABLE user_core
(
    user_id         BIGSERIAL PRIMARY KEY,
    account         VARCHAR(64)  NOT NULL UNIQUE,    -- 登录账号（手机号/邮箱/抖音号/第三方映射）
    password        VARCHAR(128) NOT NULL,           -- bcrypt 等哈希存储（禁止明文）
    nickname        VARCHAR(32)  NOT NULL,
    avatar_url      VARCHAR(255),
    register_time   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_time TIMESTAMPTZ,
    login_ip        VARCHAR(32),
    account_status  SMALLINT     NOT NULL DEFAULT 1, -- 0:禁用;1:正常;2:待验证(新注册未实名)
    is_delete       SMALLINT     NOT NULL DEFAULT 0, -- 0:未删,1:已逻辑删
    credential_version BIGINT     NOT NULL DEFAULT 1, -- 凭证版本，用于强制失效刷新令牌
    ext_json        JSONB,                           -- 扩展字段（设备/端信息等）
    CONSTRAINT chk_account_non_empty CHECK (char_length(account) > 0)
);
COMMENT ON TABLE user_core IS '所有用户的核心表（普通用户/商家人员共享）';
CREATE INDEX idx_user_core_account_status ON user_core (account_status);
-- account 字段已通过 UNIQUE 提供快速登录查询

-- 2. 实名认证表 user_realname（敏感信息需加密存储）
CREATE TABLE user_realname
(
    realname_id       BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES user_core (user_id) ON DELETE CASCADE,
    real_name         VARCHAR(32)  NOT NULL,           -- 建议存储前加密
    id_card           VARCHAR(64)  NOT NULL,           -- 建议存储前加密/脱敏展示
    id_card_front_url VARCHAR(255) NOT NULL,           -- 身份证正面照 URL
    id_card_back_url  VARCHAR(255) NOT NULL,           -- 身份证反面照 URL
    auth_status       SMALLINT     NOT NULL DEFAULT 0, -- 0:待审核,1:通过,2:驳回
    auth_time         TIMESTAMPTZ,
    reject_reason     VARCHAR(255),
    create_time       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE user_realname IS '用户实名认证记录，敏感信息隔离存储';
CREATE INDEX idx_user_realname_user_id ON user_realname (user_id);
CREATE INDEX idx_user_realname_auth_status ON user_realname (auth_status);
-- 消费者域

-- 1. 用户收货地址 consumer_address（用户多地址，支持默认地址唯一性）
CREATE TABLE consumer_address
(
    addr_id        BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES user_core (user_id) ON DELETE CASCADE,
    receiver_name  VARCHAR(32)  NOT NULL,
    receiver_phone VARCHAR(20)  NOT NULL,
    province       VARCHAR(32)  NOT NULL,
    city           VARCHAR(32)  NOT NULL,
    district       VARCHAR(32)  NOT NULL,
    detail_addr    VARCHAR(255) NOT NULL,
    is_default     SMALLINT     NOT NULL DEFAULT 0, -- 0:否,1:是（每用户仅一条）
    create_time    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE consumer_address IS '普通用户收货地址表（中间4位脱敏/加密在应用层处理）';
CREATE INDEX idx_consumer_address_user_id ON consumer_address (user_id);
-- 强制每个用户最多一条默认地址（部分唯一索引）
CREATE UNIQUE INDEX idx_consumer_address_user_default ON consumer_address (user_id) WHERE (is_default = 1);


-- 2. 普通用户详情 user_consumer（与商家隔离）
CREATE TABLE consumer_detail
(
    consumer_id        BIGSERIAL PRIMARY KEY,
    user_id            BIGINT         NOT NULL UNIQUE REFERENCES user_core (user_id) ON DELETE CASCADE,
    member_level       SMALLINT       NOT NULL DEFAULT 0,    -- 0:普通,1:白银,2:黄金,...
    total_consume      NUMERIC(12, 2) NOT NULL DEFAULT 0.00, -- 累计消费（元）
    points_balance     INTEGER        NOT NULL DEFAULT 0,
    default_addr_id    BIGINT REFERENCES consumer_address (addr_id),
    preference_tags    VARCHAR(255),
    last_purchase_time TIMESTAMPTZ
);
COMMENT ON TABLE consumer_detail IS '普通用户消费相关信息（会员/积分/偏好）';
CREATE INDEX idx_user_consumer_member_level ON consumer_detail (member_level);
-- 商家域

-- 1. 商家主体 merchant_core（商家资质信息）
CREATE TABLE merchant_core
(
    merchant_id         BIGSERIAL PRIMARY KEY,
    merchant_name       VARCHAR(64)  NOT NULL,
    merchant_type       SMALLINT     NOT NULL,           -- 1:企业,2:个体工商户,3:旗舰店
    business_license    VARCHAR(64)  NOT NULL UNIQUE,    -- 唯一验证用
    license_pic_url     VARCHAR(255) NOT NULL,
    legal_person        VARCHAR(32)  NOT NULL,           -- 建议加密存储
    legal_phone         VARCHAR(20)  NOT NULL,           -- 建议加密存储
    register_addr       VARCHAR(255) NOT NULL,
    business_scope      VARCHAR(512) NOT NULL,
    settle_bank         VARCHAR(64)  NOT NULL,
    settle_card_no      VARCHAR(64)  NOT NULL,           -- 建议加密存储
    settle_account_name VARCHAR(32)  NOT NULL,
    audit_status        SMALLINT     NOT NULL DEFAULT 0, -- 0:待提交,1:审核中,2:通过,3:驳回
    audit_time          TIMESTAMPTZ,
    reject_reason       VARCHAR(255),
    open_time           TIMESTAMPTZ,
    is_operate          SMALLINT     NOT NULL DEFAULT 0  -- 0:停业,1:营业
);
COMMENT ON TABLE merchant_core IS '商家主体资质信息（企业/个体工商户）';
CREATE INDEX idx_merchant_core_audit_status ON merchant_core (audit_status);
CREATE UNIQUE INDEX idx_merchant_core_business_license ON merchant_core (business_license);


-- 2. 商家店铺信息 merchant_shop（与主体分离，支持装修）
CREATE TABLE merchant_shop
(
    shop_id          BIGSERIAL PRIMARY KEY,
    merchant_id      BIGINT       NOT NULL REFERENCES merchant_core (merchant_id) ON DELETE CASCADE,
    shop_name        VARCHAR(64)  NOT NULL,
    shop_logo        VARCHAR(255) NOT NULL,
    shop_desc        VARCHAR(512),
    customer_service VARCHAR(20)  NOT NULL,
    business_hours   VARCHAR(128),
    shop_score       NUMERIC(2, 1)         DEFAULT 5.0,
    update_time      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE merchant_shop IS '商家店铺展示信息，与商家主体资质分离';
CREATE INDEX idx_merchant_shop_merchant_id ON merchant_shop (merchant_id);


-- 3. 角色字典 role_dict（角色枚举与描述）
CREATE TABLE role_dict
(
    role_id   SMALLINT PRIMARY KEY,
    role_name VARCHAR(32) NOT NULL,          -- 角色名称
    role_desc VARCHAR(255),                  -- 	角色描述
    is_valid  SMALLINT    NOT NULL DEFAULT 1 -- 0:废弃;1:在用
);
COMMENT ON TABLE role_dict IS '角色字典表（如普通用户/商家主账号/运营/客服/财务/仓储）';

-- 初始化角色（示例）
INSERT INTO role_dict (role_id, role_name, role_desc, is_valid)
VALUES (1, '普通用户', '抖音商城消费者', 1),
       (2, '商家主账号', '商家法定代表人/负责人账号', 1),
       (3, '商家运营', '负责商品上架、活动运营', 1),
       (4, '商家客服', '处理订单咨询、售后问题', 1),
       (5, '商家财务', '负责对账、发票管理', 1),
       (6, '商家仓储', '处理发货、库存管理', 1)
    ON CONFLICT (role_id) DO NOTHING;


-- 4. 权限字典 permission_dict（颗粒化到按钮/接口级）
CREATE TABLE permission_dict
(
    perm_id        SERIAL PRIMARY KEY,
    perm_name      VARCHAR(64) NOT NULL,
    perm_code      VARCHAR(64) NOT NULL UNIQUE, -- 前端/后端权限判定码
    perm_type      SMALLINT    NOT NULL,        -- 1:菜单,2:按钮,3:接口
    parent_perm_id INT                  DEFAULT 0,
    is_valid       SMALLINT    NOT NULL DEFAULT 1
);
COMMENT ON TABLE permission_dict IS '权限字典，支持权限树结构';
-- 示例权限数据
INSERT INTO permission_dict (perm_id, perm_name, perm_code, perm_type, parent_perm_id, is_valid)
VALUES (1000, '商品管理', 'PRODUCT_MANAGE', 1, 0, 1),
       (1001, '商品上架', 'PRODUCT_CREATE', 2, 1000, 1),
       (1002, '商品下架', 'PRODUCT_DELETE', 2, 1000, 1),
       (2000, '订单管理', 'ORDER_MANAGE', 1, 0, 1),
       (2001, '订单改价', 'ORDER_PRICE_EDIT', 2, 2000, 1)
    ON CONFLICT (perm_code) DO NOTHING;


-- 5. 用户角色关联 user_role（用户与角色绑定，支持商家角色关联 merchant_core）
CREATE TABLE user_role
(
    ur_id          BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES user_core (user_id) ON DELETE CASCADE,
    role_id        SMALLINT    NOT NULL REFERENCES role_dict (role_id),
    merchant_id    BIGINT,                         -- 当为商家内部角色时关联 merchant_core.merchant_id
    create_user_id BIGINT      NOT NULL,           -- 创建者 user_id（子账号由主账号创建）
    create_time    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role_status    SMALLINT    NOT NULL DEFAULT 1, -- 0:禁用,1:启用
    CONSTRAINT fk_user_role_merchant_fk FOREIGN KEY (merchant_id) REFERENCES merchant_core (merchant_id) ON DELETE CASCADE
);
COMMENT ON TABLE user_role IS '用户与角色关联表（支持一用户多角色与商家内角色）';
-- 避免重复绑定（同一用户同一角色在同一商家重复）
CREATE UNIQUE INDEX idx_user_role_merchant ON user_role (user_id, role_id, merchant_id);
CREATE INDEX idx_user_role_merchant_role ON user_role (merchant_id, role_id);


-- 6. 角色权限关联 role_permission（角色预设权限）
CREATE TABLE role_permission
(
    rp_id       BIGSERIAL PRIMARY KEY,
    role_id     SMALLINT    NOT NULL REFERENCES role_dict (role_id) ON DELETE CASCADE,
    perm_id     INT         NOT NULL REFERENCES permission_dict (perm_id) ON DELETE CASCADE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE role_permission IS '角色预设权限，子账号继承后可微调';
CREATE UNIQUE INDEX idx_role_permission_unique ON role_permission (role_id, perm_id);

