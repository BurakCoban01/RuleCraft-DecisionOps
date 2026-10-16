create table impact_assessment (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    client_request_id uuid not null,
    source_fingerprint varchar(64) not null,
    snapshot_fingerprint varchar(64) not null,
    risk_level varchar(16) not null,
    dependency_count integer not null,
    finding_count integer not null,
    blocker_count integer not null,
    evidence_json text not null,
    captured_by uuid references user_account(id) on delete set null,
    captured_by_name varchar(160) not null,
    captured_at timestamp with time zone not null,
    constraint uq_impact_assessment_request unique (tenant_id, decision_service_id, client_request_id),
    constraint ck_impact_assessment_risk check (risk_level in ('LOW','MEDIUM','HIGH','BLOCKED')),
    constraint ck_impact_assessment_counts check (
        dependency_count between 0 and 10000 and finding_count between 0 and 1000
        and blocker_count between 0 and finding_count
    )
);

create index ix_impact_assessment_service on impact_assessment(
    tenant_id, project_id, decision_service_id, captured_at desc
);
