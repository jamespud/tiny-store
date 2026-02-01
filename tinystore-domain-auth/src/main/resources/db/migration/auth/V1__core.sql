-- =============================================
-- 基础约定说明
-- 1. 索引命名规范：
--    - 唯一索引：uk_表名_字段1[_字段2]
--    - 普通索引：idx_表名_字段1[_字段2][_排序]（排序用asc/desc标识）
-- 2. 约束命名规范：
--    - 主键：pk_表名
--    - 外键：fk_子表名_父表名_关联字段
--    - 唯一约束：uk_表名_字段1[_字段2]
--    - 检查约束：chk_表名_字段_约束描述
-- 3. 数据类型规范：
--    - 时间字段统一使用TIMESTAMPTZ，默认值为CURRENT_TIMESTAMP
--    - IP地址统一使用INET类型（优于VARCHAR，支持IP地址运算）
--    - 版本号字段统一使用BIGINT（避免整数溢出）
--    - 文本数组字段统一为TEXT[]，默认值为'{}'::text[]
--    - 状态类字符串字段添加CHECK约束限定取值范围
-- =============================================

-- 启用pgcrypto扩展（生成UUID、加密等）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------
-- OAuth2 客户端信息表（存储已注册的OAuth2客户端）
-- ------------------------------
CREATE TABLE IF NOT EXISTS oauth2_registered_client
(
    id                            VARCHAR(100) PRIMARY KEY,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret                 VARCHAR(200),
    client_secret_expires_at      TIMESTAMPTZ,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000),
    post_logout_redirect_uris     VARCHAR(1000),
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    -- 约束定义
    CONSTRAINT uk_oauth2_registered_client_client_id UNIQUE (client_id)
);
-- 表注释
COMMENT ON TABLE oauth2_registered_client IS 'OAuth2已注册客户端信息表';
-- 字段注释
COMMENT ON COLUMN oauth2_registered_client.id IS '客户端唯一标识';
COMMENT ON COLUMN oauth2_registered_client.client_id IS '客户端ID（对外标识）';
COMMENT ON COLUMN oauth2_registered_client.client_id_issued_at IS '客户端ID签发时间';
COMMENT ON COLUMN oauth2_registered_client.client_secret IS '客户端密钥（加密存储）';
COMMENT ON COLUMN oauth2_registered_client.client_secret_expires_at IS '客户端密钥过期时间';
COMMENT ON COLUMN oauth2_registered_client.client_name IS '客户端名称';
COMMENT ON COLUMN oauth2_registered_client.client_authentication_methods IS '客户端认证方式（逗号分隔）';
COMMENT ON COLUMN oauth2_registered_client.authorization_grant_types IS '授权类型（逗号分隔）';
COMMENT ON COLUMN oauth2_registered_client.redirect_uris IS '重定向URI（逗号分隔）';
COMMENT ON COLUMN oauth2_registered_client.post_logout_redirect_uris IS '登出后重定向URI（逗号分隔）';
COMMENT ON COLUMN oauth2_registered_client.scopes IS '授权范围（逗号分隔）';
COMMENT ON COLUMN oauth2_registered_client.client_settings IS '客户端配置（JSON格式）';
COMMENT ON COLUMN oauth2_registered_client.token_settings IS '令牌配置（JSON格式）';
-- 约束注释
COMMENT ON CONSTRAINT uk_oauth2_registered_client_client_id ON oauth2_registered_client IS '客户端ID唯一';

-- ------------------------------
-- OAuth2 授权记录表（存储用户授权的令牌、授权码等信息）
-- ------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization
(
    id                            VARCHAR(100) PRIMARY KEY,
    registered_client_id          VARCHAR(100) NOT NULL,
    principal_name                VARCHAR(200) NOT NULL,
    authorization_grant_type      VARCHAR(100) NOT NULL,
    authorized_scopes             VARCHAR(1000),
    attributes                    TEXT,
    state                         VARCHAR(500),
    authorization_code_value      TEXT,
    authorization_code_issued_at  TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ,
    authorization_code_metadata   TEXT,
    access_token_value            TEXT,
    access_token_issued_at        TIMESTAMPTZ,
    access_token_expires_at       TIMESTAMPTZ,
    access_token_metadata         TEXT,
    access_token_type             VARCHAR(100),
    access_token_scopes           VARCHAR(1000),
    oidc_id_token_value           TEXT,
    oidc_id_token_issued_at       TIMESTAMPTZ,
    oidc_id_token_expires_at      TIMESTAMPTZ,
    oidc_id_token_metadata        TEXT,
    oidc_id_token_claims          VARCHAR(2000),
    refresh_token_value           TEXT,
    refresh_token_issued_at       TIMESTAMPTZ,
    refresh_token_expires_at      TIMESTAMPTZ,
    refresh_token_metadata        TEXT,
    user_code_value               TEXT,
    user_code_issued_at           TIMESTAMPTZ,
    user_code_expires_at          TIMESTAMPTZ,
    user_code_metadata            TEXT,
    device_code_value             TEXT,
    device_code_issued_at         TIMESTAMPTZ,
    device_code_expires_at        TIMESTAMPTZ,
    device_code_metadata          TEXT,
    -- 约束定义
    CONSTRAINT fk_oauth2_authorization_registered_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id) ON DELETE CASCADE
);
-- 表注释
COMMENT ON TABLE oauth2_authorization IS 'OAuth2授权记录表（存储各类令牌、授权码信息）';
-- 字段注释
COMMENT ON COLUMN oauth2_authorization.id IS '授权记录ID';
COMMENT ON COLUMN oauth2_authorization.registered_client_id IS '关联客户端ID';
COMMENT ON COLUMN oauth2_authorization.principal_name IS '授权主体（用户名/手机号）';
COMMENT ON COLUMN oauth2_authorization.authorization_grant_type IS '授权类型';
COMMENT ON COLUMN oauth2_authorization.authorized_scopes IS '已授权范围（逗号分隔）';
COMMENT ON COLUMN oauth2_authorization.attributes IS '授权附加属性（JSON格式）';
COMMENT ON COLUMN oauth2_authorization.state IS '授权状态值';
COMMENT ON COLUMN oauth2_authorization.authorization_code_value IS '授权码值';
COMMENT ON COLUMN oauth2_authorization.authorization_code_issued_at IS '授权码签发时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_expires_at IS '授权码过期时间';
COMMENT ON COLUMN oauth2_authorization.authorization_code_metadata IS '授权码元数据（JSON格式）';
COMMENT ON COLUMN oauth2_authorization.access_token_value IS '访问令牌值';
COMMENT ON COLUMN oauth2_authorization.access_token_issued_at IS '访问令牌签发时间';
COMMENT ON COLUMN oauth2_authorization.access_token_expires_at IS '访问令牌过期时间';
COMMENT ON COLUMN oauth2_authorization.access_token_metadata IS '访问令牌元数据（JSON格式）';
COMMENT ON COLUMN oauth2_authorization.access_token_type IS '访问令牌类型';
COMMENT ON COLUMN oauth2_authorization.access_token_scopes IS '访问令牌范围（逗号分隔）';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_value IS 'OIDC ID令牌值';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_issued_at IS 'OIDC ID令牌签发时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_expires_at IS 'OIDC ID令牌过期时间';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_metadata IS 'OIDC ID令牌元数据（JSON格式）';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_claims IS 'OIDC ID令牌声明（JSON格式）';
COMMENT ON COLUMN oauth2_authorization.refresh_token_value IS '刷新令牌值';
COMMENT ON COLUMN oauth2_authorization.refresh_token_issued_at IS '刷新令牌签发时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_expires_at IS '刷新令牌过期时间';
COMMENT ON COLUMN oauth2_authorization.refresh_token_metadata IS '刷新令牌元数据（JSON格式）';
COMMENT ON COLUMN oauth2_authorization.user_code_value IS '用户码值';
COMMENT ON COLUMN oauth2_authorization.user_code_issued_at IS '用户码签发时间';
COMMENT ON COLUMN oauth2_authorization.user_code_expires_at IS '用户码过期时间';
COMMENT ON COLUMN oauth2_authorization.user_code_metadata IS '用户码元数据（JSON格式）';
COMMENT ON COLUMN oauth2_authorization.device_code_value IS '设备码值';
COMMENT ON COLUMN oauth2_authorization.device_code_issued_at IS '设备码签发时间';
COMMENT ON COLUMN oauth2_authorization.device_code_expires_at IS '设备码过期时间';
COMMENT ON COLUMN oauth2_authorization.device_code_metadata IS '设备码元数据（JSON格式）';
-- 约束注释
COMMENT ON CONSTRAINT fk_oauth2_authorization_registered_client ON oauth2_authorization IS '关联OAuth2客户端表';

-- ------------------------------
-- OAuth2 授权确认表（存储用户已确认的客户端授权）
-- ------------------------------
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent
(
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    -- 约束定义
    CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (registered_client_id, principal_name),
    CONSTRAINT fk_oauth2_authorization_consent_registered_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id) ON DELETE CASCADE
);
-- 表注释
COMMENT ON TABLE oauth2_authorization_consent IS 'OAuth2授权确认表（用户确认给客户端的权限）';
-- 字段注释
COMMENT ON COLUMN oauth2_authorization_consent.registered_client_id IS '关联客户端ID';
COMMENT ON COLUMN oauth2_authorization_consent.principal_name IS '授权主体（用户名/手机号）';
COMMENT ON COLUMN oauth2_authorization_consent.authorities IS '授权的权限（逗号分隔）';
-- 约束注释
COMMENT ON CONSTRAINT pk_oauth2_authorization_consent ON oauth2_authorization_consent IS '复合主键';
COMMENT ON CONSTRAINT fk_oauth2_authorization_consent_registered_client ON oauth2_authorization_consent IS '关联OAuth2客户端表';

-- ------------------------------
-- 商城用户表（核心用户信息）
-- ------------------------------
CREATE TABLE IF NOT EXISTS mall_user
(
    id         VARCHAR(255) PRIMARY KEY,
    phone      VARCHAR(20) NOT NULL,
    nickname   VARCHAR(100),
    avatar     VARCHAR(255),
    status     VARCHAR(20) NOT NULL DEFAULT 'normal',
    rt_version BIGINT      NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 约束定义
    CONSTRAINT uk_mall_user_phone UNIQUE (phone),
    CONSTRAINT chk_mall_user_status CHECK (status IN ('normal', 'disabled', 'locked'))
);
-- 表注释
COMMENT ON TABLE mall_user IS '商城核心用户表（存储用户基础信息）';
-- 字段注释
COMMENT ON COLUMN mall_user.id IS '用户唯一标识（UUID字符串）';
COMMENT ON COLUMN mall_user.phone IS '手机号（唯一）';
COMMENT ON COLUMN mall_user.nickname IS '用户昵称';
COMMENT ON COLUMN mall_user.avatar IS '用户头像URL';
COMMENT ON COLUMN mall_user.status IS '用户状态：normal-正常，disabled-禁用，locked-锁定';
COMMENT ON COLUMN mall_user.rt_version IS '刷新令牌版本号（用于令牌失效）';
COMMENT ON COLUMN mall_user.created_at IS '创建时间';
COMMENT ON COLUMN mall_user.updated_at IS '更新时间';
-- 约束注释
COMMENT ON CONSTRAINT uk_mall_user_phone ON mall_user IS '手机号唯一';
COMMENT ON CONSTRAINT chk_mall_user_status ON mall_user IS '用户状态取值范围校验';

-- 唯一索引（与唯一约束联动，增强查询性能）
CREATE UNIQUE INDEX IF NOT EXISTS uk_mall_user_phone ON mall_user (phone);
COMMENT ON INDEX uk_mall_user_phone IS '手机号唯一索引';

-- ------------------------------
-- 登录一次性验证码表（短信验证码）
-- ------------------------------
CREATE TABLE IF NOT EXISTS login_otp
(
    id           UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    phone        VARCHAR(20) NOT NULL,
    code         VARCHAR(10) NOT NULL,
    expire_at    TIMESTAMPTZ NOT NULL,
    used         BOOLEAN     NOT NULL DEFAULT FALSE,
    retry_count  INTEGER     NOT NULL DEFAULT 0,
    send_count   INTEGER     NOT NULL DEFAULT 0,
    last_send_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 约束定义
    CONSTRAINT chk_login_otp_retry_count CHECK (retry_count >= 0),
    CONSTRAINT chk_login_otp_send_count CHECK (send_count >= 0)
);
-- 表注释
COMMENT ON TABLE login_otp IS '登录一次性验证码表（短信/邮箱验证码）';
-- 字段注释
COMMENT ON COLUMN login_otp.id IS '验证码记录ID（自动生成UUID）';
COMMENT ON COLUMN login_otp.phone IS '接收验证码的手机号';
COMMENT ON COLUMN login_otp.code IS '验证码（数字/字母组合）';
COMMENT ON COLUMN login_otp.expire_at IS '验证码过期时间';
COMMENT ON COLUMN login_otp.used IS '是否已使用';
COMMENT ON COLUMN login_otp.retry_count IS '验证码重试次数（输错次数）';
COMMENT ON COLUMN login_otp.send_count IS '验证码发送次数';
COMMENT ON COLUMN login_otp.last_send_at IS '最后一次发送时间';
COMMENT ON COLUMN login_otp.created_at IS '创建时间';
-- 约束注释
COMMENT ON CONSTRAINT chk_login_otp_retry_count ON login_otp IS '重试次数非负';
COMMENT ON CONSTRAINT chk_login_otp_send_count ON login_otp IS '发送次数非负';

-- 普通索引
CREATE INDEX IF NOT EXISTS idx_login_otp_phone ON login_otp (phone);
COMMENT ON INDEX idx_login_otp_phone IS '手机号索引（查询该手机号的验证码）';

CREATE INDEX IF NOT EXISTS idx_login_otp_expire_at ON login_otp (expire_at);
COMMENT ON INDEX idx_login_otp_expire_at IS '过期时间索引（清理过期验证码）';

CREATE INDEX IF NOT EXISTS idx_login_otp_phone_retry_count ON login_otp (phone, retry_count);
COMMENT ON INDEX idx_login_otp_phone_retry_count IS '手机号+重试次数索引（限制重试次数）';

-- ------------------------------
-- 认证审计表（记录用户认证相关操作）
-- ------------------------------
CREATE TABLE IF NOT EXISTS auth_audit
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID,
    phone      VARCHAR(20),
    client_id  VARCHAR(64),
    action     VARCHAR(64) NOT NULL,
    scopes     TEXT[]      NOT NULL DEFAULT '{}'::text[],
    ip         INET,
    user_agent TEXT,
    details    TEXT,
    success    BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 表注释
COMMENT ON TABLE auth_audit IS '认证审计表（记录用户登录、授权等操作日志）';
-- 字段注释
COMMENT ON COLUMN auth_audit.id IS '审计记录ID（自增）';
COMMENT ON COLUMN auth_audit.user_id IS '用户ID（关联mall_user.id）';
COMMENT ON COLUMN auth_audit.phone IS '用户手机号';
COMMENT ON COLUMN auth_audit.client_id IS '客户端ID';
COMMENT ON COLUMN auth_audit.action IS '操作类型：login-登录，logout-登出，refresh_token-刷新令牌，authorize-授权等';
COMMENT ON COLUMN auth_audit.scopes IS '授权范围（数组）';
COMMENT ON COLUMN auth_audit.ip IS '操作IP地址';
COMMENT ON COLUMN auth_audit.user_agent IS '用户代理（浏览器/设备信息）';
COMMENT ON COLUMN auth_audit.details IS '操作详情（JSON格式）';
COMMENT ON COLUMN auth_audit.success IS '操作是否成功';
COMMENT ON COLUMN auth_audit.created_at IS '操作时间';

-- 普通索引
CREATE INDEX IF NOT EXISTS idx_auth_audit_user_id_created_at_desc ON auth_audit (user_id, created_at DESC);
COMMENT ON INDEX idx_auth_audit_user_id_created_at_desc IS '用户ID+创建时间倒序索引（查询用户操作记录）';

CREATE INDEX IF NOT EXISTS idx_auth_audit_action ON auth_audit (action);
COMMENT ON INDEX idx_auth_audit_action IS '操作类型索引（统计各类操作次数）';

-- ------------------------------
-- 授权同意历史表（记录用户授权范围变更）
-- ------------------------------
CREATE TABLE IF NOT EXISTS auth_consent_history
(
    id             BIGSERIAL PRIMARY KEY,
    user_id        UUID        NOT NULL,
    client_id      VARCHAR(64) NOT NULL,
    added_scopes   TEXT[]      NOT NULL DEFAULT '{}'::text[],
    removed_scopes TEXT[]      NOT NULL DEFAULT '{}'::text[],
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 表注释
COMMENT ON TABLE auth_consent_history IS '授权同意历史表（记录用户对客户端授权范围的变更）';
-- 字段注释
COMMENT ON COLUMN auth_consent_history.id IS '历史记录ID（自增）';
COMMENT ON COLUMN auth_consent_history.user_id IS '用户ID';
COMMENT ON COLUMN auth_consent_history.client_id IS '客户端ID';
COMMENT ON COLUMN auth_consent_history.added_scopes IS '新增的授权范围';
COMMENT ON COLUMN auth_consent_history.removed_scopes IS '移除的授权范围';
COMMENT ON COLUMN auth_consent_history.created_at IS '记录创建时间';

-- 普通索引
CREATE INDEX IF NOT EXISTS idx_auth_consent_history_user_id_created_at_desc ON auth_consent_history (user_id, created_at DESC);
COMMENT ON INDEX idx_auth_consent_history_user_id_created_at_desc IS '用户ID+创建时间倒序索引（查询用户授权变更记录）';

-- ------------------------------
-- 认证事件投递表（Outbox模式，用于事件驱动）
-- ------------------------------
CREATE TABLE IF NOT EXISTS auth_outbox
(
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(120) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    rt_version     BIGINT       NOT NULL,
    reason         VARCHAR(255),
    client_id      VARCHAR(128),
    added_scopes   TEXT,
    removed_scopes TEXT,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    published      BOOLEAN      NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMPTZ,
    -- 约束定义
    CONSTRAINT uk_auth_outbox_aggregate_id_rt_version UNIQUE (aggregate_id, rt_version)
);
-- 表注释
COMMENT ON TABLE auth_outbox IS '认证事件投递表（Outbox模式，保证事件可靠投递）';
-- 字段注释
COMMENT ON COLUMN auth_outbox.id IS '事件记录ID（自增）';
COMMENT ON COLUMN auth_outbox.event_type IS '事件类型：user_login-用户登录，consent_change-授权变更等';
COMMENT ON COLUMN auth_outbox.aggregate_id IS '聚合根ID（用户ID）';
COMMENT ON COLUMN auth_outbox.rt_version IS '刷新令牌版本号';
COMMENT ON COLUMN auth_outbox.reason IS '事件原因描述';
COMMENT ON COLUMN auth_outbox.client_id IS '客户端ID';
COMMENT ON COLUMN auth_outbox.added_scopes IS '新增授权范围（JSON字符串）';
COMMENT ON COLUMN auth_outbox.removed_scopes IS '移除授权范围（JSON字符串）';
COMMENT ON COLUMN auth_outbox.occurred_at IS '事件发生时间';
COMMENT ON COLUMN auth_outbox.published IS '是否已投递';
COMMENT ON COLUMN auth_outbox.published_at IS '投递时间';
-- 约束注释
COMMENT ON CONSTRAINT uk_auth_outbox_aggregate_id_rt_version ON auth_outbox IS '聚合根ID+版本号唯一（避免重复事件）';

-- 索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_outbox_aggregate_id_rt_version ON auth_outbox (aggregate_id, rt_version);
COMMENT ON INDEX uk_auth_outbox_aggregate_id_rt_version IS '聚合根ID+版本号唯一索引';

CREATE INDEX IF NOT EXISTS idx_auth_outbox_published ON auth_outbox (published);
COMMENT ON INDEX idx_auth_outbox_published IS '投递状态索引（查询未投递事件）';

CREATE INDEX IF NOT EXISTS idx_auth_outbox_event_type_published_occurred_at ON auth_outbox (event_type, published, occurred_at);
COMMENT ON INDEX idx_auth_outbox_event_type_published_occurred_at IS '事件类型+投递状态+发生时间索引（筛选未投递事件）';

-- ------------------------------
-- 通用审计日志表（全局操作审计）
-- ------------------------------
CREATE TABLE IF NOT EXISTS audit_log
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    VARCHAR(64),
    phone      VARCHAR(32),
    client_id  VARCHAR(128),
    action     VARCHAR(64) NOT NULL,
    scopes     TEXT,
    ip         INET,
    user_agent VARCHAR(256),
    details    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 表注释
COMMENT ON TABLE audit_log IS '通用审计日志表（全系统操作审计）';
-- 字段注释
COMMENT ON COLUMN audit_log.id IS '审计日志ID（自增）';
COMMENT ON COLUMN audit_log.user_id IS '用户ID';
COMMENT ON COLUMN audit_log.phone IS '用户手机号';
COMMENT ON COLUMN audit_log.client_id IS '客户端ID';
COMMENT ON COLUMN audit_log.action IS '操作类型';
COMMENT ON COLUMN audit_log.scopes IS '授权范围（JSON字符串）';
COMMENT ON COLUMN audit_log.ip IS '操作IP地址（统一使用INET类型）';
COMMENT ON COLUMN audit_log.user_agent IS '用户代理';
COMMENT ON COLUMN audit_log.details IS '操作详情（JSON格式）';
COMMENT ON COLUMN audit_log.created_at IS '操作时间';

-- 普通索引
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
COMMENT ON INDEX idx_audit_log_created_at IS '创建时间索引（按时间查询日志）';

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_log (user_id);
COMMENT ON INDEX idx_audit_log_user_id IS '用户ID索引（查询用户操作日志）';

-- =============================================
-- 可选优化：添加update_time自动更新触发器
-- 适用于mall_user表的updated_at字段自动更新
-- =============================================
CREATE OR REPLACE FUNCTION update_timestamp_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
COMMENT ON FUNCTION update_timestamp_column() IS '自动更新时间戳字段的触发器函数';

-- 为mall_user添加更新时间触发器
CREATE TRIGGER trg_mall_user_updated_at
    BEFORE UPDATE
    ON mall_user
    FOR EACH ROW
EXECUTE FUNCTION update_timestamp_column();
COMMENT ON TRIGGER trg_mall_user_updated_at ON mall_user IS '更新mall_user.updated_at字段的触发器';