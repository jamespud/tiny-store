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
