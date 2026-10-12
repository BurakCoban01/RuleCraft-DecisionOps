create table runtime_benchmark_run (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    deployment_id uuid not null references runtime_deployment(id) on delete cascade,
    decision_version_id uuid not null references decision_version(id),
    client_request_id uuid not null,
    request_fingerprint varchar(64) not null,
    artifact_fingerprint varchar(64) not null,
    input_fingerprint varchar(64) not null,
    output_fingerprint varchar(64) not null,
    iterations integer not null,
    warmup_iterations integer not null,
    total_duration_micros bigint not null,
    min_micros bigint not null,
    mean_micros bigint not null,
    p50_micros bigint not null,
    p95_micros bigint not null,
    p99_micros bigint not null,
    max_micros bigint not null,
    throughput_per_second double precision not null,
    captured_by uuid references user_account(id) on delete set null,
    captured_by_name varchar(160) not null,
    captured_at timestamp with time zone not null,
    constraint uq_runtime_benchmark_request unique (tenant_id, decision_service_id, client_request_id),
    constraint ck_runtime_benchmark_iterations check (iterations between 10 and 500 and warmup_iterations between 0 and 50),
    constraint ck_runtime_benchmark_latency check (
        total_duration_micros >= 0 and min_micros >= 0 and min_micros <= p50_micros
        and p50_micros <= p95_micros and p95_micros <= p99_micros and p99_micros <= max_micros
        and mean_micros between min_micros and max_micros and throughput_per_second > 0
    )
);

create index ix_runtime_benchmark_service on runtime_benchmark_run(
    tenant_id, project_id, decision_service_id, captured_at desc
);
