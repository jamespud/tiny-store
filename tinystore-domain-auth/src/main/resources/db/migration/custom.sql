create table if not exists mall_user (
     id varchar(255) primary key,
     phone varchar(20) not null,
    nickname varchar(100),
    avatar varchar(255),
    status varchar(20) not null default 'normal',
    rt_version integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
    );

create unique index if not exists mall_user_phone_uindex on mall_user (phone);

create table if not exists login_otp (
                                         id uuid primary key,
                                         phone varchar(20) not null,
    code varchar(10) not null,
    expire_at timestamptz not null,
    used boolean not null default false,
    created_at timestamptz not null default now()
    );

create index if not exists login_otp_phone_idx on login_otp (phone);
create index if not exists login_otp_expire_idx on login_otp (expire_at);

create table if not exists auth_audit (
                                          id bigserial primary key,
                                          user_id uuid,
                                          phone varchar(20),
    client_id varchar(64),
    action varchar(64) not null,
    scopes text[],
    ip inet,
    user_agent text,
    details text,
    success boolean not null,
    created_at timestamptz not null default now()
    );

create index if not exists auth_audit_user_created_idx on auth_audit (user_id, created_at desc);
create index if not exists auth_audit_action_idx on auth_audit (action);

create table if not exists auth_outbox (
                                           id bigserial primary key,
                                           event_type varchar(120) not null,
    aggregate_id uuid not null,
    rt_version bigint not null,
    reason varchar(255),
    occurred_at timestamptz not null,
    published boolean not null default false,
    published_at timestamptz
    );

create unique index if not exists auth_outbox_user_version_uindex on auth_outbox (aggregate_id, rt_version);
create index if not exists auth_outbox_published_idx on auth_outbox (published);

alter table if exists login_otp
    add column if not exists retry_count integer not null default 0,
    add column if not exists send_count integer not null default 0,
    add column if not exists last_send_at timestamptz;

create index if not exists login_otp_retry_idx on login_otp (phone, retry_count);

create table if not exists auth_consent_history (
                                                    id bigserial primary key,
                                                    user_id uuid not null,
                                                    client_id varchar(64) not null,
    added_scopes text[] not null default '{}',
    removed_scopes text[] not null default '{}',
    created_at timestamptz not null default now()
    );

create index if not exists auth_consent_history_user_idx on auth_consent_history (user_id, created_at desc);
