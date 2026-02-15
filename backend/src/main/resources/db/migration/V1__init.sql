create table app_user (
  id bigserial primary key,
  email varchar(255) not null unique,
  password_hash varchar(255) not null,
  role varchar(20) not null,
  created_at timestamptz not null default now()
);

create table refresh_token (
  id bigserial primary key,
  user_id bigint not null references app_user(id) on delete cascade,
  token_hash varchar(255) not null,
  jti varchar(80) not null unique,
  expires_at timestamptz not null,
  revoked boolean not null default false,
  created_at timestamptz not null default now()
);

create index ix_refresh_user on refresh_token(user_id);

create table refund_record (
  id bigserial primary key,
  user_id bigint not null references app_user(id) on delete cascade,
  tax_year int not null,
  status varchar(40) not null,
  last_updated_at timestamptz not null default now(),
  expected_amount numeric(18,2),
  irs_tracking_id varchar(100),
  available_at_estimated timestamptz,
  unique(user_id, tax_year)
);

create index ix_refund_user_last on refund_record(user_id, last_updated_at desc);

create table ai_request_log (
  id bigserial primary key,
  user_id bigint references app_user(id) on delete set null,
  request_type varchar(40) not null,
  input_payload text not null,
  provider varchar(40) not null,
  model varchar(80),
  success boolean not null,
  output_payload text,
  error_message text,
  helpful boolean,
  created_at timestamptz not null default now()
);

create index ix_ai_user_created on ai_request_log(user_id, created_at desc);
