-- 客户端信息表
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id VARCHAR(100) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMP,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    CONSTRAINT uk_client_id UNIQUE (client_id)
);

-- 授权记录表
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMP,
    authorization_code_expires_at TIMESTAMP,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMP,
    access_token_expires_at TIMESTAMP,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMP,
    oidc_id_token_expires_at TIMESTAMP,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMP,
    refresh_token_expires_at TIMESTAMP,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMP,
    user_code_expires_at TIMESTAMP,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMP,
    device_code_expires_at TIMESTAMP,
    device_code_metadata TEXT,
    CONSTRAINT fk_oauth2_auth_registered_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id) ON DELETE CASCADE
);

-- 授权确认表
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name),
    CONSTRAINT fk_oauth2_consent_registered_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id) ON DELETE CASCADE
);

-- 用户表
CREATE TABLE IF NOT EXISTS mall_user (
    id VARCHAR(255) PRIMARY KEY,
    phone VARCHAR(20) NOT NULL,
    nickname VARCHAR(100),
    avatar VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'normal',
    rt_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS mall_user_phone_uindex ON mall_user (phone);

-- 登录一次性验证码
CREATE TABLE IF NOT EXISTS login_otp (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL,
    code VARCHAR(10) NOT NULL,
    expire_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INTEGER NOT NULL DEFAULT 0,
    send_count INTEGER NOT NULL DEFAULT 0,
    last_send_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS login_otp_phone_idx ON login_otp (phone);
CREATE INDEX IF NOT EXISTS login_otp_expire_idx ON login_otp (expire_at);
CREATE INDEX IF NOT EXISTS login_otp_retry_idx ON login_otp (phone, retry_count);

-- 审计表（auth_audit）
CREATE TABLE IF NOT EXISTS auth_audit (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID,
    phone VARCHAR(20),
    client_id VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    scopes TEXT[] NOT NULL DEFAULT '{}'::text[],
    ip INET,
    user_agent TEXT,
    details TEXT,
    success BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS auth_audit_user_created_idx ON auth_audit (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS auth_audit_action_idx ON auth_audit (action);

-- Auth consent history
CREATE TABLE IF NOT EXISTS auth_consent_history (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    added_scopes TEXT[] NOT NULL DEFAULT '{}'::text[],
    removed_scopes TEXT[] NOT NULL DEFAULT '{}'::text[],
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS auth_consent_history_user_idx ON auth_consent_history (user_id, created_at DESC);

-- Outbox 表（合并、采用 uuid 作为 aggregate_id）
CREATE TABLE IF NOT EXISTS auth_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(120) NOT NULL,
    aggregate_id UUID NOT NULL,
    rt_version BIGINT NOT NULL,
    reason VARCHAR(255),
    client_id VARCHAR(128),
    added_scopes TEXT,
    removed_scopes TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS auth_outbox_user_version_uindex ON auth_outbox (aggregate_id, rt_version);
CREATE INDEX IF NOT EXISTS auth_outbox_published_idx ON auth_outbox (published);
CREATE INDEX IF NOT EXISTS idx_auth_outbox_unpublished ON auth_outbox (event_type, published, occurred_at);

-- 通用审计日志（与 JdbcAuditLogAdapter 对应）
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64),
    phone VARCHAR(32),
    client_id VARCHAR(128),
    action VARCHAR(64) NOT NULL,
    scopes TEXT, -- keep as text (JSON string if needed)
    ip VARCHAR(64),
    user_agent VARCHAR(256),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_user ON audit_log (user_id);
