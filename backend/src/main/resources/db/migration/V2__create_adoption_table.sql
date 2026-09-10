create table if not exists adoption_applications (
    id bigserial primary key,
    cat_id bigint not null,
    adopter_name varchar(120) not null,
    adopter_email varchar(200) not null,
    status varchar(20) not null default 'PENDING',
    notes text,
    created_at timestamptz not null default now(),
    constraint fk_adoption_cat foreign key (cat_id) references cats(id)
);
