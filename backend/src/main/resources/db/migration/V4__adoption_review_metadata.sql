alter table adoption_applications add column updated_at timestamptz;
update adoption_applications set updated_at = created_at where updated_at is null;
alter table adoption_applications alter column updated_at set not null;
alter table adoption_applications alter column updated_at set default now();

create index idx_adoptions_status_created on adoption_applications(status, created_at desc, id desc);
create index idx_adoptions_cat_status on adoption_applications(cat_id, status);
