-- Historical snapshot emails are not evidence of ownership. Leave existing rows NULL.
alter table adoption_applications add column adopter_account_id bigint;
alter table adoption_applications add constraint fk_adoption_account
    foreign key (adopter_account_id) references user_accounts(id) on delete restrict;

create index idx_adoptions_owner_created
    on adoption_applications(adopter_account_id, created_at desc, id desc);
-- Pending duplicate checks remain serialized by the existing Cat lock.
