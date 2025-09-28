create table if not exists mall_user (
    id uuid primary key,
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
