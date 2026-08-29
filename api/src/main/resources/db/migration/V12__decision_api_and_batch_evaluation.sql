create table api_credential (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    label varchar(160) not null,
    key_prefix varchar(24) not null,
    key_hash varchar(64) not null,
    status varchar(16) not null,
    created_by uuid not null references user_account(id),
    created_by_name varchar(160) not null,
    created_at timestamp with time zone not null,
    revoked_by uuid references user_account(id),
    revoked_at timestamp with time zone,
    version bigint not null default 0,
    constraint uq_api_credential_hash unique (key_hash),
    constraint ck_api_credential_status check (status in ('ACTIVE','REVOKED'))
);

create index ix_api_credential_service on api_credential(tenant_id, project_id, decision_service_id, created_at desc);

create table decision_api_request (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    credential_id uuid references api_credential(id),
    subject_key varchar(80) not null,
    client_request_id uuid not null,
    operation varchar(16) not null,
    request_hash varchar(64) not null,
    deployment_id uuid not null references runtime_deployment(id),
    decision_version_id uuid not null references decision_version(id),
    artifact_fingerprint varchar(64) not null,
    item_count integer not null,
    response_json text not null,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone not null,
    constraint uq_decision_api_request unique (tenant_id, decision_service_id, subject_key, client_request_id),
    constraint ck_decision_api_operation check (operation in ('SINGLE','BATCH')),
    constraint ck_decision_api_item_count check (item_count between 1 and 100)
);

create index ix_decision_api_history on decision_api_request(tenant_id, project_id, decision_service_id, created_at desc);
