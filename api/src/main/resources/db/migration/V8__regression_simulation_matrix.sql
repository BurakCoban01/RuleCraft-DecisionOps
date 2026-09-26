create table regression_matrix_run (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    client_request_id uuid not null,
    selection_json text not null,
    status varchar(16) not null,
    selected_count integer not null,
    passed_count integer not null default 0,
    failed_count integer not null default 0,
    error_count integer not null default 0,
    initiated_by uuid not null references user_account(id),
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint uq_regression_matrix_request unique (tenant_id, decision_service_id, client_request_id),
    constraint ck_regression_matrix_status check (status in ('RUNNING', 'COMPLETED', 'INTERRUPTED')),
    constraint ck_regression_matrix_counts check (selected_count between 1 and 100 and passed_count >= 0 and failed_count >= 0 and error_count >= 0)
);

create table regression_matrix_result (
    id uuid primary key,
    matrix_run_id uuid not null references regression_matrix_run(id) on delete cascade,
    position integer not null,
    test_case_id uuid not null,
    test_case_version bigint not null,
    test_key varchar(60) not null,
    test_name varchar(160) not null,
    status varchar(16) not null,
    test_run_id uuid,
    evaluation_id uuid,
    graph_version bigint,
    expected_outputs_json text not null,
    actual_outputs_json text,
    decision_results_json text,
    error_code varchar(80),
    error_detail varchar(500),
    executed_at timestamp with time zone not null,
    constraint uq_regression_matrix_position unique (matrix_run_id, position),
    constraint uq_regression_matrix_case unique (matrix_run_id, test_case_id),
    constraint ck_regression_matrix_result_status check (status in ('PASSED', 'FAILED', 'ERROR'))
);

create index ix_regression_matrix_service on regression_matrix_run(tenant_id, project_id, decision_service_id, started_at desc);
create index ix_regression_matrix_results on regression_matrix_result(matrix_run_id, position);
