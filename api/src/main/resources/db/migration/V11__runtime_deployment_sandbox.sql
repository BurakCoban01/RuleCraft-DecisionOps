create table runtime_environment (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    environment_key varchar(40) not null,
    name varchar(160) not null,
    active_deployment_id uuid,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    revision bigint not null default 0,
    constraint uq_runtime_environment unique (tenant_id, decision_service_id, environment_key)
);

create table runtime_deployment (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    environment_id uuid not null references runtime_environment(id) on delete cascade,
    decision_version_id uuid not null references decision_version(id),
    change_request_id uuid not null references decision_change_request(id),
    client_request_id uuid not null,
    sequence_number integer not null,
    state varchar(24) not null,
    artifact_json text not null,
    artifact_fingerprint varchar(64) not null,
    failure_code varchar(80),
    failure_detail varchar(600),
    deployed_by uuid not null references user_account(id),
    deployed_by_name varchar(160) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    revision bigint not null default 0,
    constraint uq_runtime_deployment_client unique (tenant_id, decision_service_id, client_request_id),
    constraint uq_runtime_deployment_sequence unique (environment_id, sequence_number),
    constraint ck_runtime_deployment_sequence check (sequence_number between 1 and 10000),
    constraint ck_runtime_deployment_state check (state in ('CREATED','VALIDATING','ACTIVE','FAILED','ROLLED_BACK','RETIRED'))
);

alter table runtime_environment add constraint fk_runtime_environment_active
    foreign key (active_deployment_id) references runtime_deployment(id);

create table runtime_deployment_event (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    deployment_id uuid not null references runtime_deployment(id) on delete cascade,
    event_type varchar(32) not null,
    from_state varchar(24),
    to_state varchar(24) not null,
    actor_id uuid not null references user_account(id),
    actor_name varchar(160) not null,
    note varchar(600),
    created_at timestamp with time zone not null
);

create index ix_runtime_deployment_history on runtime_deployment(environment_id, sequence_number desc);
create index ix_runtime_event_history on runtime_deployment_event(deployment_id, created_at asc);
