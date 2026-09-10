create table user_accounts (
    id bigserial primary key,
    email varchar(200) not null,
    display_name varchar(120) not null,
    password_hash varchar(255) not null,
    role varchar(20) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_user_accounts_email unique (email),
    constraint ck_user_accounts_role check (role in ('STAFF', 'ADOPTER')),
    constraint ck_user_accounts_email_normalized check (email = lower(btrim(email)))
);
