create table decision_trace (
    id uuid primary key,
    evaluation_id uuid not null,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    deployment_id uuid not null references runtime_deployment(id) on delete cascade,
    decision_version_id uuid not null references decision_version(id),
    credential_id uuid references api_credential(id) on delete set null,
    actor_user_id uuid references user_account(id) on delete set null,
    subject_label varchar(160) not null,
    client_request_id uuid,
    correlation_id varchar(80),
    channel varchar(24) not null,
    status varchar(16) not null,
    artifact_fingerprint varchar(64) not null,
    input_fingerprint varchar(64) not null,
    output_fingerprint varchar(64),
    input_keys_json text not null,
    output_keys_json text not null,
    error_code varchar(80),
    error_detail varchar(600),
    step_count integer not null,
    duration_micros bigint not null,
    created_at timestamp with time zone not null,
    constraint uq_decision_trace_evaluation unique (evaluation_id),
    constraint ck_decision_trace_channel check (channel in ('SANDBOX','API_SINGLE','API_BATCH','WORKBENCH_BATCH')),
    constraint ck_decision_trace_status check (status in ('SUCCEEDED','FAILED')),
    constraint ck_decision_trace_step_count check (step_count between 0 and 10000),
    constraint ck_decision_trace_duration check (duration_micros >= 0)
);

create index ix_decision_trace_service on decision_trace(tenant_id, project_id, decision_service_id, created_at desc);
create index ix_decision_trace_request on decision_trace(tenant_id, decision_service_id, client_request_id);
create index ix_decision_trace_credential on decision_trace(tenant_id, decision_service_id, credential_id, created_at desc);

create table decision_trace_step (
    id uuid primary key,
    trace_id uuid not null references decision_trace(id) on delete cascade,
    position integer not null,
    node_key varchar(120) not null,
    node_name varchar(160) not null,
    implementation varchar(16) not null,
    hit_policy varchar(16),
    matched_rule_id varchar(80),
    input_keys_json text not null,
    result_json text not null,
    constraint uq_decision_trace_step unique (trace_id, position),
    constraint ck_decision_trace_step_position check (position between 0 and 9999),
    constraint ck_decision_trace_implementation check (implementation in ('TABLE','EXPRESSION'))
);

create index ix_decision_trace_step_trace on decision_trace_step(trace_id, position);
