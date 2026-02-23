alter table outbox_event
  add column locked_at timestamptz,
  add column locked_by varchar(80);

create index if not exists ix_outbox_lock on outbox_event(locked_at) where processed_at is null;