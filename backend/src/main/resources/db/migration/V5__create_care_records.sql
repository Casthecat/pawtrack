create table care_records (
    id bigserial primary key,
    cat_id bigint not null references cats(id) on delete cascade,
    type varchar(30) not null,
    note text not null,
    created_at timestamptz not null default now()
);

create index idx_care_records_cat_created
    on care_records(cat_id, created_at desc, id desc);
