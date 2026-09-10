alter table cats
    add column if not exists stream_url varchar(500),
    add column if not exists image_url varchar(500);
