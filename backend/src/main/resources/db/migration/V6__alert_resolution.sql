-- Historical CLOSED alerts retain NULL: their actual resolution time is unknown.
alter table alerts add column resolved_at timestamptz;

alter table care_records
    add column alert_id bigint references alerts(id) on delete set null;

create index idx_care_records_alert on care_records(alert_id);
