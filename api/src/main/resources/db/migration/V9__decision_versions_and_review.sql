create table decision_version (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    client_request_id uuid not null,
    version_number integer not null,
    label varchar(160) not null,
    note varchar(1000),
    validation_status varchar(16) not null,
    fingerprint varchar(64) not null,
    snapshot_json text not null,
    source_service_version bigint not null,
    source_graph_version bigint not null,
    captured_by uuid not null references user_account(id),
    captured_at timestamp with time zone not null,
    constraint uq_decision_version_number unique (tenant_id, decision_service_id, version_number),
    constraint uq_decision_version_request unique (tenant_id, decision_service_id, client_request_id),
    constraint ck_decision_version_number check (version_number between 1 and 1000),
    constraint ck_decision_version_status check (validation_status in ('VALID', 'INVALID'))
);

create index ix_decision_version_service on decision_version(tenant_id, project_id, decision_service_id, version_number desc);
