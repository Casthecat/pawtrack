-- The legacy column remains historical data only; the typed backend does not map it.
do $$
begin
    if exists (select 1 from cats where status is null
               or status not in ('NORMAL', 'ADOPTABLE', 'UNDER_OBSERVATION', 'SICK', 'ADOPTED')) then
        raise exception 'Cannot migrate cats: unsupported legacy status. Inspect cats.status before retrying V7.';
    end if;
end $$;

alter table cats add column health_status varchar(30), add column adoption_status varchar(30);

-- ADOPTED previously erased the independent health dimension; NORMAL is a
-- historical compatibility assumption, not a reconstruction of medical history.
update cats set health_status = case status
    when 'NORMAL' then 'NORMAL'
    when 'ADOPTABLE' then 'NORMAL'
    when 'UNDER_OBSERVATION' then 'UNDER_OBSERVATION'
    when 'SICK' then 'SICK'
    when 'ADOPTED' then 'NORMAL'
end,
adoption_status = case when status = 'ADOPTED' then 'ADOPTED' else 'AVAILABLE' end;

alter table cats
    alter column health_status set not null,
    alter column health_status set default 'NORMAL',
    alter column adoption_status set not null,
    alter column adoption_status set default 'AVAILABLE';
