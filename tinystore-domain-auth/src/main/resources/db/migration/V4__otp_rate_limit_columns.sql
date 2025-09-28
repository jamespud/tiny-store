alter table if exists login_otp
    add column if not exists retry_count integer not null default 0,
    add column if not exists send_count integer not null default 0,
    add column if not exists last_send_at timestamptz;

create index if not exists login_otp_retry_idx on login_otp (phone, retry_count);
