create table operational_recovery_run (
    id uuid primary key,
    tenant_id uuid not null references tenant(id) on delete cascade,
    client_request_id uuid not null,
    source_fingerprint varchar(64) not null,
    result_fingerprint varchar(64) not null,
    interrupted_matrices integer not null,
    cleared_runtime_pointers integer not null,
    status varchar(16) not null,
    executed_by uuid references user_account(id) on delete set null,
    executed_by_name varchar(160) not null,
    executed_at timestamp with time zone not null,
    constraint uq_operational_recovery_request unique (tenant_id, client_request_id),
    constraint ck_operational_recovery_counts check (interrupted_matrices >= 0 and cleared_runtime_pointers >= 0),
    constraint ck_operational_recovery_status check (status in ('READY', 'DEGRADED'))
);

create index ix_operational_recovery_tenant on operational_recovery_run(tenant_id, executed_at desc);
