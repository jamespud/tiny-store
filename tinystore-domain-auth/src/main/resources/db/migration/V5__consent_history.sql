create table if not exists auth_consent_history (
    id bigserial primary key,
    user_id uuid not null,
    client_id varchar(64) not null,
    added_scopes text[] not null default '{}',
    removed_scopes text[] not null default '{}',
    created_at timestamptz not null default now()
);

create index if not exists auth_consent_history_user_idx on auth_consent_history (user_id, created_at desc);
