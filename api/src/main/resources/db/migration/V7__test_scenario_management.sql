create table decision_test_case (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    test_key varchar(60) not null,
    name varchar(160) not null,
    description varchar(500),
    inputs_json text not null,
    expected_outputs_json text not null,
    validation_status varchar(16) not null,
    created_by uuid not null references user_account(id),
    updated_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_decision_test_case_key unique (tenant_id, decision_service_id, test_key),
    constraint ck_decision_test_case_status check (validation_status in ('VALID', 'INVALID'))
);

create table decision_test_run (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    test_case_id uuid not null references decision_test_case(id) on delete cascade,
    test_case_version bigint not null,
    evaluation_id uuid not null,
    graph_version bigint not null,
    status varchar(16) not null,
    expected_outputs_json text not null,
    actual_outputs_json text not null,
    decision_results_json text not null,
    executed_by uuid not null references user_account(id),
    executed_at timestamp with time zone not null,
    constraint ck_decision_test_run_status check (status in ('PASSED', 'FAILED'))
);

create index ix_decision_test_case_service on decision_test_case(tenant_id, project_id, decision_service_id);
create index ix_decision_test_run_case on decision_test_run(tenant_id, test_case_id, executed_at desc);
