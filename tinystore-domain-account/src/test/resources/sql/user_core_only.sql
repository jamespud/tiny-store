-- 仅用于并发集成测试的最小化 user_core 表定义
CREATE TABLE IF NOT EXISTS user_core
(
    user_id         BIGSERIAL PRIMARY KEY,
    account         VARCHAR(64)  NOT NULL UNIQUE,
    password        VARCHAR(128) NOT NULL,
    nickname        VARCHAR(32)  NOT NULL,
    avatar_url      VARCHAR(255),
    register_time   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_time TIMESTAMPTZ,
    login_ip        VARCHAR(32),
    account_status  SMALLINT     NOT NULL DEFAULT 1,
    is_delete       SMALLINT     NOT NULL DEFAULT 0,
    credential_version BIGINT     NOT NULL DEFAULT 1,
    ext_json        JSONB,
    CONSTRAINT chk_account_non_empty CHECK (char_length(account) > 0)
);

CREATE INDEX IF NOT EXISTS idx_user_core_account_status ON user_core (account_status);
