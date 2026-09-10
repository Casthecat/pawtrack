create table if not exists cats (
                                    id bigserial primary key,
                                    name varchar(100) not null,
    status varchar(50) not null default 'NORMAL',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
    );

create table if not exists health_data (
                                           id bigserial primary key,
                                           cat_id bigint not null references cats(id) on delete cascade,
    ts timestamptz not null,
    temperature_c numeric(5,2),
    activity_level int,
    created_at timestamptz not null default now()
    );

create index if not exists idx_health_data_cat_ts on health_data(cat_id, ts);

create table if not exists alerts (
                                      id bigserial primary key,
                                      cat_id bigint not null references cats(id) on delete cascade,
    type varchar(50) not null,
    status varchar(50) not null default 'OPEN',
    severity varchar(20) not null default 'MEDIUM',
    message text,
    created_at timestamptz not null default now(),
    acknowledged_at timestamptz
    );

create index if not exists idx_alerts_cat_created on alerts(cat_id, created_at);

create table if not exists users (
                                     id bigserial primary key,
                                     email varchar(200) unique not null,
    name varchar(100),
    role varchar(50) not null default 'STAFF',
    created_at timestamptz not null default now()
    );
