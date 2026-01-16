-- auth_user: auth 域的用户表（user_id 对齐 account，不自增；phone 唯一）
CREATE TABLE auth_user (
    user_id BIGINT PRIMARY KEY,
    phone VARCHAR(32) NOT NULL,
    password VARCHAR(255) NOT NULL,
    account_status INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_user_phone UNIQUE (phone)
);

CREATE INDEX idx_auth_user_phone ON auth_user(phone);
