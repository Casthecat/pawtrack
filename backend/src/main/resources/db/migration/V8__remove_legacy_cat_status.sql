-- V7 already backfilled these authoritative fields. Never read legacy status again.
do $$
begin
    if (select count(*) from information_schema.columns
        where table_schema = current_schema() and table_name = 'cats'
          and column_name in ('health_status', 'adoption_status')
          and is_nullable = 'NO') <> 2
       or exists (select 1 from cats where health_status is null or adoption_status is null) then
        raise exception 'Cannot remove legacy cat status: typed columns must be non-null before V8.';
    end if;
end $$;

alter table cats drop column status;
