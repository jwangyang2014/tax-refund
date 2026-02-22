create table refund_access_audit (
  id bigserial primary key,
  user_id bigint not null references app_user(id) on delete cascade,
  endpoint varchar(120) not null,
  success boolean not null,
  occurred_at timestamptz not null default now(),
  correlation_id varchar(80)
);

create index ix_refund_audit_user_time on refund_access_audit(user_id, occurred_at desc);
create index ix_refund_audit_time on refund_access_audit(occurred_at desc);