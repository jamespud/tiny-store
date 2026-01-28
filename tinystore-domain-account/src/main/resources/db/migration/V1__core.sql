-- =============================================
-- 基础约定说明
-- 1. 索引命名规范：
--    - 唯一索引：uk_表名_字段1[_字段2...]
--    - 普通索引：idx_表名_字段1[_字段2...]
--    - 部分唯一索引：uk_表名_字段_条件（注释说明条件）
-- 2. 约束命名规范：
--    - 外键：fk_子表名_父表名_关联字段
--    - 检查约束：chk_表名_字段_约束描述
--    - 唯一约束：uk_表名_字段
-- 3. 数据类型规范：
--    - 状态类SMALLINT字段添加CHECK约束限定取值范围
--    - 时间字段统一为TIMESTAMPTZ，默认值CURRENT_TIMESTAMP
--    - 手机号/证件号等敏感字段标注加密建议，状态字段明确取值含义
-- =============================================

-- ------------------------------
-- 公共域 - 核心用户主表
-- ------------------------------
CREATE TABLE user_core
(
    user_id            BIGSERIAL PRIMARY KEY,
    account            VARCHAR(64)  NOT NULL,
    password           VARCHAR(128) NOT NULL,
    nickname           VARCHAR(32)  NOT NULL,
    avatar_url         VARCHAR(255),
    register_time      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_time    TIMESTAMPTZ,
    login_ip           VARCHAR(32),
    account_status     SMALLINT     NOT NULL DEFAULT 1,
    is_delete          SMALLINT     NOT NULL DEFAULT 0,
    credential_version BIGINT       NOT NULL DEFAULT 1,
    ext_json           JSONB,
    -- 约束定义
    CONSTRAINT uk_user_core_account UNIQUE (account),
    CONSTRAINT chk_user_core_account_non_empty CHECK (char_length(account) > 0),
    CONSTRAINT chk_user_core_account_status CHECK (account_status IN (0, 1, 2)),
    CONSTRAINT chk_user_core_is_delete CHECK (is_delete IN (0, 1))
);

-- 表注释
COMMENT ON TABLE user_core IS '所有用户的核心表（普通用户/商家人员共享）';
-- 字段注释
COMMENT ON COLUMN user_core.user_id IS '用户唯一标识，自增主键';
COMMENT ON COLUMN user_core.account IS '登录账号（手机号/邮箱/抖音号/第三方映射）';
COMMENT ON COLUMN user_core.password IS '密码（bcrypt等哈希存储，禁止明文）';
COMMENT ON COLUMN user_core.nickname IS '用户昵称';
COMMENT ON COLUMN user_core.avatar_url IS '用户头像URL';
COMMENT ON COLUMN user_core.register_time IS '注册时间';
COMMENT ON COLUMN user_core.last_login_time IS '最后登录时间';
COMMENT ON COLUMN user_core.login_ip IS '最后登录IP地址';
COMMENT ON COLUMN user_core.account_status IS '账号状态：0-禁用，1-正常，2-待验证(新注册未实名)';
COMMENT ON COLUMN user_core.is_delete IS '逻辑删除标识：0-未删除，1-已删除';
COMMENT ON COLUMN user_core.credential_version IS '凭证版本号，用于强制失效刷新令牌';
COMMENT ON COLUMN user_core.ext_json IS '扩展字段（设备/端信息等）';
-- 约束注释
COMMENT ON CONSTRAINT uk_user_core_account ON user_core IS '登录账号唯一';
COMMENT ON CONSTRAINT chk_user_core_account_non_empty ON user_core IS '账号非空校验';
COMMENT ON CONSTRAINT chk_user_core_account_status ON user_core IS '账号状态取值范围校验';
COMMENT ON CONSTRAINT chk_user_core_is_delete ON user_core IS '逻辑删除标识取值范围校验';

-- 普通索引
CREATE INDEX idx_user_core_account_status ON user_core (account_status);
COMMENT ON INDEX idx_user_core_account_status IS '账号状态索引，用于账号状态筛选查询';

-- ------------------------------
-- 公共域 - 用户实名认证表（敏感信息隔离）
-- ------------------------------
CREATE TABLE user_realname
(
    realname_id       BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    real_name         VARCHAR(32)  NOT NULL,
    id_card           VARCHAR(64)  NOT NULL,
    id_card_front_url VARCHAR(255) NOT NULL,
    id_card_back_url  VARCHAR(255) NOT NULL,
    auth_status       SMALLINT     NOT NULL DEFAULT 0,
    auth_time         TIMESTAMPTZ,
    reject_reason     VARCHAR(255),
    create_time       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 约束定义
    CONSTRAINT fk_user_realname_user_core FOREIGN KEY (user_id) REFERENCES user_core (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_user_realname_auth_status CHECK (auth_status IN (0, 1, 2))
);

-- 表注释
COMMENT ON TABLE user_realname IS '用户实名认证记录，敏感信息隔离存储';
-- 字段注释
COMMENT ON COLUMN user_realname.realname_id IS '实名认证记录ID，自增主键';
COMMENT ON COLUMN user_realname.user_id IS '关联用户核心表ID';
COMMENT ON COLUMN user_realname.real_name IS '真实姓名（建议存储前加密）';
COMMENT ON COLUMN user_realname.id_card IS '身份证号（建议存储前加密/脱敏展示）';
COMMENT ON COLUMN user_realname.id_card_front_url IS '身份证正面照URL';
COMMENT ON COLUMN user_realname.id_card_back_url IS '身份证反面照URL';
COMMENT ON COLUMN user_realname.auth_status IS '审核状态：0-待审核，1-通过，2-驳回';
COMMENT ON COLUMN user_realname.auth_time IS '审核完成时间';
COMMENT ON COLUMN user_realname.reject_reason IS '驳回原因';
COMMENT ON COLUMN user_realname.create_time IS '记录创建时间';
-- 约束注释
COMMENT ON CONSTRAINT fk_user_realname_user_core ON user_realname IS '关联用户核心表，用户删除则实名认证记录同步删除';
COMMENT ON CONSTRAINT chk_user_realname_auth_status ON user_realname IS '审核状态取值范围校验';

-- 普通索引
CREATE INDEX idx_user_realname_user_id ON user_realname (user_id);
COMMENT ON INDEX idx_user_realname_user_id IS '用户ID索引，用于查询用户实名认证记录';

CREATE INDEX idx_user_realname_auth_status ON user_realname (auth_status);
COMMENT ON INDEX idx_user_realname_auth_status IS '审核状态索引，用于筛选不同审核状态的实名认证记录';

-- ------------------------------
-- 消费者域 - 用户收货地址表
-- ------------------------------
CREATE TABLE consumer_address
(
    addr_id        BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    receiver_name  VARCHAR(32)  NOT NULL,
    receiver_phone VARCHAR(20)  NOT NULL,
    province       VARCHAR(32)  NOT NULL,
    city           VARCHAR(32)  NOT NULL,
    district       VARCHAR(32)  NOT NULL,
    detail_addr    VARCHAR(255) NOT NULL,
    is_default     SMALLINT     NOT NULL DEFAULT 0,
    create_time    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 约束定义
    CONSTRAINT fk_consumer_address_user_core FOREIGN KEY (user_id) REFERENCES user_core (user_id) ON DELETE CASCADE,
    CONSTRAINT chk_consumer_address_is_default CHECK (is_default IN (0, 1))
);

-- 表注释
COMMENT ON TABLE consumer_address IS '普通用户收货地址表（手机号中间4位脱敏/加密在应用层处理）';
-- 字段注释
COMMENT ON COLUMN consumer_address.addr_id IS '地址ID，自增主键';
COMMENT ON COLUMN consumer_address.user_id IS '关联用户核心表ID';
COMMENT ON COLUMN consumer_address.receiver_name IS '收件人姓名';
COMMENT ON COLUMN consumer_address.receiver_phone IS '收件人手机号';
COMMENT ON COLUMN consumer_address.province IS '省份';
COMMENT ON COLUMN consumer_address.city IS '城市';
COMMENT ON COLUMN consumer_address.district IS '区县';
COMMENT ON COLUMN consumer_address.detail_addr IS '详细地址';
COMMENT ON COLUMN consumer_address.is_default IS '默认地址标识：0-否，1-是（每用户仅一条）';
COMMENT ON COLUMN consumer_address.create_time IS '创建时间';
COMMENT ON COLUMN consumer_address.update_time IS '更新时间';
-- 约束注释
COMMENT ON CONSTRAINT fk_consumer_address_user_core ON consumer_address IS '关联用户核心表，用户删除则地址同步删除';
COMMENT ON CONSTRAINT chk_consumer_address_is_default ON consumer_address IS '默认地址标识取值范围校验';

-- 普通索引
CREATE INDEX idx_consumer_address_user_id ON consumer_address (user_id);
COMMENT ON INDEX idx_consumer_address_user_id IS '用户ID索引，用于查询用户所有收货地址';
-- 部分唯一索引：保证每个用户仅一条默认地址
CREATE UNIQUE INDEX uk_consumer_address_user_default ON consumer_address (user_id) WHERE (is_default = 1);
COMMENT ON INDEX uk_consumer_address_user_default IS '部分唯一索引：保证每个用户仅一条默认地址（is_default=1）';


-- ------------------------------
-- 消费者域 - 普通用户详情表
-- ------------------------------
CREATE TABLE consumer_detail
(
    consumer_id        BIGSERIAL PRIMARY KEY,
    user_id            BIGINT         NOT NULL,
    member_level       SMALLINT       NOT NULL DEFAULT 0,
    total_consume      NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    points_balance     INTEGER        NOT NULL DEFAULT 0,
    default_addr_id    BIGINT,
    preference_tags    VARCHAR(255),
    last_purchase_time TIMESTAMPTZ,
    -- 约束定义
    CONSTRAINT fk_consumer_detail_user_core FOREIGN KEY (user_id) REFERENCES user_core (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_consumer_detail_consumer_address FOREIGN KEY (default_addr_id) REFERENCES consumer_address (addr_id) ON DELETE SET NULL,
    CONSTRAINT uk_consumer_detail_user_id UNIQUE (user_id),
    CONSTRAINT chk_consumer_detail_member_level CHECK (member_level >= 0)
);

-- 表注释
COMMENT ON TABLE consumer_detail IS '普通用户消费相关信息（会员/积分/偏好）';
-- 字段注释
COMMENT ON COLUMN consumer_detail.consumer_id IS '消费者详情ID，自增主键';
COMMENT ON COLUMN consumer_detail.user_id IS '关联用户核心表ID（唯一）';
COMMENT ON COLUMN consumer_detail.member_level IS '会员等级：0-普通，1-白银，2-黄金...';
COMMENT ON COLUMN consumer_detail.total_consume IS '累计消费金额（元）';
COMMENT ON COLUMN consumer_detail.points_balance IS '积分余额';
COMMENT ON COLUMN consumer_detail.default_addr_id IS '默认收货地址ID（关联consumer_address.addr_id）';
COMMENT ON COLUMN consumer_detail.preference_tags IS '用户偏好标签（逗号分隔）';
COMMENT ON COLUMN consumer_detail.last_purchase_time IS '最后一次消费时间';
-- 约束注释
COMMENT ON CONSTRAINT fk_consumer_detail_user_core ON consumer_detail IS '关联用户核心表，用户删除则详情同步删除';
COMMENT ON CONSTRAINT fk_consumer_detail_consumer_address ON consumer_detail IS '关联收货地址表，地址删除则默认地址置空';
COMMENT ON CONSTRAINT uk_consumer_detail_user_id ON consumer_detail IS '用户ID唯一，一个用户仅一条详情记录';
COMMENT ON CONSTRAINT chk_consumer_detail_member_level ON consumer_detail IS '会员等级非负校验';

-- 普通索引
CREATE INDEX idx_consumer_detail_member_level ON consumer_detail (member_level);
COMMENT ON INDEX idx_consumer_detail_member_level IS '会员等级索引，用于会员等级筛选查询';

-- ------------------------------
-- 商家域 - 商家主体资质表
-- ------------------------------
CREATE TABLE merchant_core
(
    merchant_id         BIGSERIAL PRIMARY KEY,
    merchant_name       VARCHAR(64)  NOT NULL,
    merchant_type       SMALLINT     NOT NULL,
    business_license    VARCHAR(64)  NOT NULL,
    license_pic_url     VARCHAR(255) NOT NULL,
    legal_person        VARCHAR(32)  NOT NULL,
    legal_phone         VARCHAR(20)  NOT NULL,
    register_addr       VARCHAR(255) NOT NULL,
    business_scope      VARCHAR(512) NOT NULL,
    settle_bank         VARCHAR(64)  NOT NULL,
    settle_card_no      VARCHAR(64)  NOT NULL,
    settle_account_name VARCHAR(32)  NOT NULL,
    audit_status        SMALLINT     NOT NULL DEFAULT 0,
    audit_time          TIMESTAMPTZ,
    reject_reason       VARCHAR(255),
    open_time           TIMESTAMPTZ,
    is_operate          SMALLINT     NOT NULL DEFAULT 0,
    -- 约束定义
    CONSTRAINT uk_merchant_core_business_license UNIQUE (business_license),
    CONSTRAINT chk_merchant_core_merchant_type CHECK (merchant_type IN (1, 2, 3)),
    CONSTRAINT chk_merchant_core_audit_status CHECK (audit_status IN (0, 1, 2, 3)),
    CONSTRAINT chk_merchant_core_is_operate CHECK (is_operate IN (0, 1))
);

-- 表注释
COMMENT ON TABLE merchant_core IS '商家主体资质信息（企业/个体工商户）';
-- 字段注释
COMMENT ON COLUMN merchant_core.merchant_id IS '商家ID，自增主键';
COMMENT ON COLUMN merchant_core.merchant_name IS '商家名称';
COMMENT ON COLUMN merchant_core.merchant_type IS '商家类型：1-企业，2-个体工商户，3-旗舰店';
COMMENT ON COLUMN merchant_core.business_license IS '营业执照编号（唯一）';
COMMENT ON COLUMN merchant_core.license_pic_url IS '营业执照图片URL';
COMMENT ON COLUMN merchant_core.legal_person IS '法定代表人姓名（建议加密存储）';
COMMENT ON COLUMN merchant_core.legal_phone IS '法定代表人手机号（建议加密存储）';
COMMENT ON COLUMN merchant_core.register_addr IS '注册地址';
COMMENT ON COLUMN merchant_core.business_scope IS '经营范围';
COMMENT ON COLUMN merchant_core.settle_bank IS '结算银行名称';
COMMENT ON COLUMN merchant_core.settle_card_no IS '结算银行卡号（建议加密存储）';
COMMENT ON COLUMN merchant_core.settle_account_name IS '结算账户名';
COMMENT ON COLUMN merchant_core.audit_status IS '审核状态：0-待提交，1-审核中，2-通过，3-驳回';
COMMENT ON COLUMN merchant_core.audit_time IS '审核完成时间';
COMMENT ON COLUMN merchant_core.reject_reason IS '驳回原因';
COMMENT ON COLUMN merchant_core.open_time IS '开业时间';
COMMENT ON COLUMN merchant_core.is_operate IS '营业状态：0-停业，1-营业';
-- 约束注释
COMMENT ON CONSTRAINT uk_merchant_core_business_license ON merchant_core IS '营业执照编号唯一';
COMMENT ON CONSTRAINT chk_merchant_core_merchant_type ON merchant_core IS '商家类型取值范围校验';
COMMENT ON CONSTRAINT chk_merchant_core_audit_status ON merchant_core IS '审核状态取值范围校验';
COMMENT ON CONSTRAINT chk_merchant_core_is_operate ON merchant_core IS '营业状态取值范围校验';

-- 普通索引
CREATE INDEX idx_merchant_core_audit_status ON merchant_core (audit_status);
COMMENT ON INDEX idx_merchant_core_audit_status IS '审核状态索引，用于筛选不同审核状态的商家';

-- ------------------------------
-- 商家域 - 商家店铺信息表
-- ------------------------------
CREATE TABLE merchant_shop
(
    shop_id          BIGSERIAL PRIMARY KEY,
    merchant_id      BIGINT       NOT NULL,
    shop_name        VARCHAR(64)  NOT NULL,
    shop_logo        VARCHAR(255) NOT NULL,
    shop_desc        VARCHAR(512),
    customer_service VARCHAR(20)  NOT NULL,
    business_hours   VARCHAR(128),
    shop_score       NUMERIC(2, 1)         DEFAULT 5.0,
    update_time      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 约束定义
    CONSTRAINT fk_merchant_shop_merchant_core FOREIGN KEY (merchant_id) REFERENCES merchant_core (merchant_id) ON DELETE CASCADE,
    CONSTRAINT chk_merchant_shop_shop_score CHECK (shop_score BETWEEN 0.0 AND 5.0)
);

-- 表注释
COMMENT ON TABLE merchant_shop IS '商家店铺展示信息，与商家主体资质分离';
-- 字段注释
COMMENT ON COLUMN merchant_shop.shop_id IS '店铺ID，自增主键';
COMMENT ON COLUMN merchant_shop.merchant_id IS '关联商家主体表ID';
COMMENT ON COLUMN merchant_shop.shop_name IS '店铺名称';
COMMENT ON COLUMN merchant_shop.shop_logo IS '店铺LOGO URL';
COMMENT ON COLUMN merchant_shop.shop_desc IS '店铺描述';
COMMENT ON COLUMN merchant_shop.customer_service IS '客服手机号';
COMMENT ON COLUMN merchant_shop.business_hours IS '营业时间（格式：09:00-22:00）';
COMMENT ON COLUMN merchant_shop.shop_score IS '店铺评分（1.0-5.0）';
COMMENT ON COLUMN merchant_shop.update_time IS '更新时间';
-- 约束注释
COMMENT ON CONSTRAINT fk_merchant_shop_merchant_core ON merchant_shop IS '关联商家主体表，商家删除则店铺同步删除';
COMMENT ON CONSTRAINT chk_merchant_shop_shop_score ON merchant_shop IS '店铺评分取值范围校验（0.0-5.0）';

-- 普通索引
CREATE INDEX idx_merchant_shop_merchant_id ON merchant_shop (merchant_id);
COMMENT ON INDEX idx_merchant_shop_merchant_id IS '商家ID索引，用于查询商家所有店铺';

-- ------------------------------
-- 权限域 - 角色字典表
-- ------------------------------
CREATE TABLE role_dict
(
    role_id   SMALLINT PRIMARY KEY,
    role_name VARCHAR(32) NOT NULL,
    role_desc VARCHAR(255),
    is_valid  SMALLINT    NOT NULL DEFAULT 1,
    -- 约束定义
    CONSTRAINT chk_role_dict_is_valid CHECK (is_valid IN (0, 1))
);

-- 表注释
COMMENT ON TABLE role_dict IS '角色字典表（如普通用户/商家主账号/运营/客服/财务/仓储）';
-- 字段注释
COMMENT ON COLUMN role_dict.role_id IS '角色ID，枚举值';
COMMENT ON COLUMN role_dict.role_name IS '角色名称';
COMMENT ON COLUMN role_dict.role_desc IS '角色描述';
COMMENT ON COLUMN role_dict.is_valid IS '是否有效：0-废弃，1-在用';
-- 约束注释
COMMENT ON CONSTRAINT chk_role_dict_is_valid ON role_dict IS '有效性标识取值范围校验';

-- 初始化角色数据
INSERT INTO role_dict (role_id, role_name, role_desc, is_valid)
VALUES (1, '普通用户', '抖音商城消费者', 1),
       (2, '商家主账号', '商家法定代表人/负责人账号', 1),
       (3, '商家运营', '负责商品上架、活动运营', 1),
       (4, '商家客服', '处理订单咨询、售后问题', 1),
       (5, '商家财务', '负责对账、发票管理', 1),
       (6, '商家仓储', '处理发货、库存管理', 1)
ON CONFLICT (role_id) DO NOTHING;

-- ------------------------------
-- 权限域 - 权限字典表
-- ------------------------------
CREATE TABLE permission_dict
(
    perm_id        SERIAL PRIMARY KEY,
    perm_name      VARCHAR(64) NOT NULL,
    perm_code      VARCHAR(64) NOT NULL,
    perm_type      SMALLINT    NOT NULL,
    parent_perm_id INT                  DEFAULT 0,
    is_valid       SMALLINT    NOT NULL DEFAULT 1,
    -- 约束定义
    CONSTRAINT uk_permission_dict_perm_code UNIQUE (perm_code),
    CONSTRAINT chk_permission_dict_perm_type CHECK (perm_type IN (1, 2, 3)),
    CONSTRAINT chk_permission_dict_is_valid CHECK (is_valid IN (0, 1))
);

-- 表注释
COMMENT ON TABLE permission_dict IS '权限字典，支持权限树结构';
-- 字段注释
COMMENT ON COLUMN permission_dict.perm_id IS '权限ID，自增主键';
COMMENT ON COLUMN permission_dict.perm_name IS '权限名称';
COMMENT ON COLUMN permission_dict.perm_code IS '前后端权限判定码（唯一）';
COMMENT ON COLUMN permission_dict.perm_type IS '权限类型：1-菜单，2-按钮，3-接口';
COMMENT ON COLUMN permission_dict.parent_perm_id IS '父权限ID（0表示顶级权限）';
COMMENT ON COLUMN permission_dict.is_valid IS '是否有效：0-废弃，1-在用';
-- 约束注释
COMMENT ON CONSTRAINT uk_permission_dict_perm_code ON permission_dict IS '权限编码唯一';
COMMENT ON CONSTRAINT chk_permission_dict_perm_type ON permission_dict IS '权限类型取值范围校验';
COMMENT ON CONSTRAINT chk_permission_dict_is_valid ON permission_dict IS '有效性标识取值范围校验';

-- 初始化权限数据
INSERT INTO permission_dict (perm_id, perm_name, perm_code, perm_type, parent_perm_id, is_valid)
VALUES (1000, '商品管理', 'PRODUCT_MANAGE', 1, 0, 1),
       (1001, '商品上架', 'PRODUCT_CREATE', 2, 1000, 1),
       (1002, '商品下架', 'PRODUCT_DELETE', 2, 1000, 1),
       (2000, '订单管理', 'ORDER_MANAGE', 1, 0, 1),
       (2001, '订单改价', 'ORDER_PRICE_EDIT', 2, 2000, 1)
ON CONFLICT (perm_code) DO NOTHING;

-- ------------------------------
-- 权限域 - 用户角色关联表
-- ------------------------------
CREATE TABLE user_role
(
    ur_id          BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    role_id        SMALLINT    NOT NULL,
    merchant_id    BIGINT,
    create_user_id BIGINT      NOT NULL,
    create_time    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role_status    SMALLINT    NOT NULL DEFAULT 1,
    -- 约束定义
    CONSTRAINT fk_user_role_user_core FOREIGN KEY (user_id) REFERENCES user_core (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role_dict FOREIGN KEY (role_id) REFERENCES role_dict (role_id),
    CONSTRAINT fk_user_role_merchant_core FOREIGN KEY (merchant_id) REFERENCES merchant_core (merchant_id) ON DELETE CASCADE,
    CONSTRAINT uk_user_role_user_role_merchant UNIQUE (user_id, role_id, merchant_id),
    CONSTRAINT chk_user_role_role_status CHECK (role_status IN (0, 1))
);

-- 表注释
COMMENT ON TABLE user_role IS '用户与角色关联表（支持一用户多角色与商家内角色）';
-- 字段注释
COMMENT ON COLUMN user_role.ur_id IS '关联记录ID，自增主键';
COMMENT ON COLUMN user_role.user_id IS '关联用户核心表ID';
COMMENT ON COLUMN user_role.role_id IS '关联角色字典表ID';
COMMENT ON COLUMN user_role.merchant_id IS '关联商家主体表ID（仅商家角色需填写）';
COMMENT ON COLUMN user_role.create_user_id IS '创建者用户ID（子账号由主账号创建）';
COMMENT ON COLUMN user_role.create_time IS '创建时间';
COMMENT ON COLUMN user_role.role_status IS '角色状态：0-禁用，1-启用';
-- 约束注释
COMMENT ON CONSTRAINT fk_user_role_user_core ON user_role IS '关联用户核心表，用户删除则角色关联同步删除';
COMMENT ON CONSTRAINT fk_user_role_role_dict ON user_role IS '关联角色字典表';
COMMENT ON CONSTRAINT fk_user_role_merchant_core ON user_role IS '关联商家主体表，商家删除则角色关联同步删除';
COMMENT ON CONSTRAINT uk_user_role_user_role_merchant ON user_role IS '避免同一用户同一角色在同一商家重复绑定';
COMMENT ON CONSTRAINT chk_user_role_role_status ON user_role IS '角色状态取值范围校验';

-- 索引
CREATE INDEX idx_user_role_merchant_role ON user_role (merchant_id, role_id);
COMMENT ON INDEX idx_user_role_merchant_role IS '商家ID+角色ID索引，用于查询商家特定角色的用户';

-- ------------------------------
-- 权限域 - 角色权限关联表
-- ------------------------------
CREATE TABLE role_permission
(
    rp_id       BIGSERIAL PRIMARY KEY,
    role_id     SMALLINT    NOT NULL,
    perm_id     INT         NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 约束定义
    CONSTRAINT fk_role_permission_role_dict FOREIGN KEY (role_id) REFERENCES role_dict (role_id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission_dict FOREIGN KEY (perm_id) REFERENCES permission_dict (perm_id) ON DELETE CASCADE,
    CONSTRAINT uk_role_permission_role_perm UNIQUE (role_id, perm_id)
);

-- 表注释
COMMENT ON TABLE role_permission IS '角色预设权限，子账号继承后可微调';
-- 字段注释
COMMENT ON COLUMN role_permission.rp_id IS '关联记录ID，自增主键';
COMMENT ON COLUMN role_permission.role_id IS '关联角色字典表ID';
COMMENT ON COLUMN role_permission.perm_id IS '关联权限字典表ID';
COMMENT ON COLUMN role_permission.create_time IS '创建时间';
-- 约束注释
COMMENT ON CONSTRAINT fk_role_permission_role_dict ON role_permission IS '关联角色字典表，角色删除则权限关联同步删除';
COMMENT ON CONSTRAINT fk_role_permission_permission_dict ON role_permission IS '关联权限字典表，权限删除则角色关联同步删除';
COMMENT ON CONSTRAINT uk_role_permission_role_perm ON role_permission IS '避免同一角色重复绑定同一权限';

-- =============================================
-- 可选优化：添加update_time自动更新触发器
-- 适用于consumer_address、merchant_shop的update_time字段
-- =============================================
CREATE OR REPLACE FUNCTION update_timestamp_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
COMMENT ON FUNCTION update_timestamp_column() IS '自动更新时间戳字段的触发器函数';

-- 为consumer_address添加更新时间触发器
CREATE TRIGGER trg_consumer_address_update_time
    BEFORE UPDATE
    ON consumer_address
    FOR EACH ROW
EXECUTE FUNCTION update_timestamp_column();
COMMENT ON TRIGGER trg_consumer_address_update_time ON consumer_address IS '自动更新consumer_address.update_time字段';

-- 为merchant_shop添加更新时间触发器
CREATE TRIGGER trg_merchant_shop_update_time
    BEFORE UPDATE
    ON merchant_shop
    FOR EACH ROW
EXECUTE FUNCTION update_timestamp_column();
COMMENT ON TRIGGER trg_merchant_shop_update_time ON merchant_shop IS '自动更新merchant_shop.update_time字段';