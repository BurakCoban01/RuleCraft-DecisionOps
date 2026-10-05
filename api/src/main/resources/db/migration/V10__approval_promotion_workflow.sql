create table decision_change_request (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    decision_version_id uuid not null references decision_version(id),
    client_request_id uuid not null,
    request_number integer not null,
    title varchar(160) not null,
    summary varchar(2000),
    state varchar(32) not null,
    created_by uuid not null references user_account(id),
    created_by_name varchar(160) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_change_request_version unique (tenant_id, decision_service_id, decision_version_id),
    constraint uq_change_request_client unique (tenant_id, decision_service_id, client_request_id),
    constraint uq_change_request_number unique (tenant_id, decision_service_id, request_number),
    constraint ck_change_request_number check (request_number between 1 and 1000),
    constraint ck_change_request_state check (state in ('DRAFT','SUBMITTED','CHANGES_REQUESTED','APPROVED','REJECTED','PROMOTED'))
);

create table decision_change_event (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    change_request_id uuid not null references decision_change_request(id) on delete cascade,
    client_request_id uuid not null,
    event_type varchar(32) not null,
    from_state varchar(32),
    to_state varchar(32) not null,
    note varchar(2000),
    actor_id uuid not null references user_account(id),
    actor_name varchar(160) not null,
    created_at timestamp with time zone not null,
    constraint uq_change_event_client unique (change_request_id, client_request_id)
);

create table decision_promotion (
    decision_service_id uuid primary key references decision_service(id) on delete cascade,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    change_request_id uuid not null unique references decision_change_request(id),
    decision_version_id uuid not null references decision_version(id),
    promoted_by uuid not null references user_account(id),
    promoted_by_name varchar(160) not null,
    promoted_at timestamp with time zone not null,
    revision bigint not null default 0
);

create index ix_change_request_service on decision_change_request(tenant_id, project_id, decision_service_id, request_number desc);
create index ix_change_event_request on decision_change_event(change_request_id, created_at asc);
