CREATE TABLE oauth2_registered_client
(
    id                            varchar(100)                            NOT NULL,
    client_id                     varchar(100)                            NOT NULL,
    client_id_issued_at           timestamp     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret                 varchar(200)  DEFAULT NULL,
    client_secret_expires_at      timestamp     DEFAULT NULL,
    client_name                   varchar(200)                            NOT NULL,
    client_authentication_methods varchar(1000)                           NOT NULL,
    authorization_grant_types     varchar(1000)                           NOT NULL,
    redirect_uris                 varchar(1000) DEFAULT NULL,
    post_logout_redirect_uris     varchar(1000) DEFAULT NULL,
    scopes                        varchar(1000)                           NOT NULL,
    client_settings               varchar(2000)                           NOT NULL,
    token_settings                varchar(2000)                           NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization_consent
(
    registered_client_id varchar(100)  NOT NULL,
    principal_name       varchar(200)  NOT NULL,
    authorities          varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE oauth2_authorization
(
    id                            varchar(100) NOT NULL,
    registered_client_id          varchar(100) NOT NULL,
    principal_name                varchar(200) NOT NULL,
    authorization_grant_type      varchar(100) NOT NULL,
    authorized_scopes             varchar(1000) DEFAULT NULL,
    attributes                    text          DEFAULT NULL,
    state                         varchar(500)  DEFAULT NULL,
    authorization_code_value      text          DEFAULT NULL,
    authorization_code_issued_at  timestamp     DEFAULT NULL,
    authorization_code_expires_at timestamp     DEFAULT NULL,
    authorization_code_metadata   text          DEFAULT NULL,
    access_token_value            text          DEFAULT NULL,
    access_token_issued_at        timestamp     DEFAULT NULL,
    access_token_expires_at       timestamp     DEFAULT NULL,
    access_token_metadata         text          DEFAULT NULL,
    access_token_type             varchar(100)  DEFAULT NULL,
    access_token_scopes           varchar(1000) DEFAULT NULL,
    oidc_id_token_value           text          DEFAULT NULL,
    oidc_id_token_issued_at       timestamp     DEFAULT NULL,
    oidc_id_token_expires_at      timestamp     DEFAULT NULL,
    oidc_id_token_metadata        text          DEFAULT NULL,
    refresh_token_value           text          DEFAULT NULL,
    refresh_token_issued_at       timestamp     DEFAULT NULL,
    refresh_token_expires_at      timestamp     DEFAULT NULL,
    refresh_token_metadata        text          DEFAULT NULL,
    user_code_value               text          DEFAULT NULL,
    user_code_issued_at           timestamp     DEFAULT NULL,
    user_code_expires_at          timestamp     DEFAULT NULL,
    user_code_metadata            text          DEFAULT NULL,
    device_code_value             text          DEFAULT NULL,
    device_code_issued_at         timestamp     DEFAULT NULL,
    device_code_expires_at        timestamp     DEFAULT NULL,
    device_code_metadata          text          DEFAULT NULL,
    PRIMARY KEY (id)
);

create table if not exists mall_user
(
    id         varchar(255) primary key,
    phone      varchar(20) not null,
    nickname   varchar(100),
    avatar     varchar(255),
    status     varchar(20) not null default 'normal',
    rt_version integer     not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists mall_user_phone_uindex on mall_user (phone);

create table if not exists login_otp
(
    id         uuid primary key,
    phone      varchar(20) not null,
    code       varchar(10) not null,
    expire_at  timestamptz not null,
    used       boolean     not null default false,
    created_at timestamptz not null default now()
);

create index if not exists login_otp_phone_idx on login_otp (phone);
create index if not exists login_otp_expire_idx on login_otp (expire_at);

create table if not exists auth_audit
(
    id         bigserial primary key,
    user_id    uuid,
    phone      varchar(20),
    client_id  varchar(64),
    action     varchar(64) not null,
    scopes     text[],
    ip         inet,
    user_agent text,
    details    text,
    success    boolean     not null,
    created_at timestamptz not null default now()
);

create index if not exists auth_audit_user_created_idx on auth_audit (user_id, created_at desc);
create index if not exists auth_audit_action_idx on auth_audit (action);

create table if not exists auth_outbox
(
    id           bigserial primary key,
    event_type   varchar(120) not null,
    aggregate_id uuid         not null,
    rt_version   bigint       not null,
    reason       varchar(255),
    occurred_at  timestamptz  not null,
    published    boolean      not null default false,
    published_at timestamptz
);

create unique index if not exists auth_outbox_user_version_uindex on auth_outbox (aggregate_id, rt_version);
create index if not exists auth_outbox_published_idx on auth_outbox (published);

alter table if exists login_otp
    add column if not exists retry_count  integer not null default 0,
    add column if not exists send_count   integer not null default 0,
    add column if not exists last_send_at timestamptz;

create index if not exists login_otp_retry_idx on login_otp (phone, retry_count);

create table if not exists auth_consent_history
(
    id             bigserial primary key,
    user_id        uuid        not null,
    client_id      varchar(64) not null,
    added_scopes   text[]      not null default '{}',
    removed_scopes text[]      not null default '{}',
    created_at     timestamptz not null default now()
);

create index if not exists auth_consent_history_user_idx on auth_consent_history (user_id, created_at desc);
