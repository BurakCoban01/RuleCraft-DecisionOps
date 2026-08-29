create table decision_expression_definition (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    graph_id uuid not null references decision_graph(id) on delete cascade,
    node_id uuid not null references graph_node(id) on delete cascade,
    language_version varchar(16) not null,
    source_text varchar(12000) not null,
    validation_status varchar(16) not null,
    created_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_decision_expression_node unique (node_id),
    constraint ck_decision_expression_language check (language_version = 'RCEL-1'),
    constraint ck_decision_expression_status check (validation_status in ('VALID', 'INVALID')),
    constraint ck_decision_expression_source check (length(source_text) <= 12000)
);

create index ix_decision_expression_tenant_project on decision_expression_definition(tenant_id, project_id);
create index ix_decision_expression_graph on decision_expression_definition(graph_id);
