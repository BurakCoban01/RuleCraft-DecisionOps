create table decision_table_definition (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    graph_id uuid not null references decision_graph(id) on delete cascade,
    node_id uuid not null references graph_node(id) on delete cascade,
    hit_policy varchar(16) not null,
    validation_status varchar(16) not null,
    created_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_decision_table_node unique (node_id),
    constraint ck_decision_table_hit_policy check (hit_policy in ('UNIQUE', 'FIRST')),
    constraint ck_decision_table_status check (validation_status in ('VALID', 'INVALID'))
);

create index ix_decision_table_tenant_project on decision_table_definition(tenant_id, project_id);
create index ix_decision_table_graph on decision_table_definition(graph_id);

create table decision_table_rule (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    decision_table_id uuid not null references decision_table_definition(id) on delete cascade,
    position integer not null,
    enabled boolean not null,
    description varchar(300),
    output_value_json varchar(4000),
    constraint uq_decision_table_rule_position unique (decision_table_id, position),
    constraint ck_decision_table_rule_position check (position >= 0)
);

create index ix_decision_table_rule_table on decision_table_rule(decision_table_id);

create table decision_table_input_entry (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    decision_table_id uuid not null references decision_table_definition(id) on delete cascade,
    rule_id uuid not null references decision_table_rule(id) on delete cascade,
    input_port_id uuid not null references graph_port(id) on delete cascade,
    operator varchar(16) not null,
    value_json varchar(4000),
    constraint uq_decision_table_entry_port unique (rule_id, input_port_id),
    constraint ck_decision_table_entry_operator check (operator in ('ANY', 'EQUALS')),
    constraint ck_decision_table_entry_value check ((operator = 'ANY' and value_json is null)
        or (operator = 'EQUALS' and value_json is not null and length(value_json) between 1 and 4000))
);

create index ix_decision_table_entry_table on decision_table_input_entry(decision_table_id);
create index ix_decision_table_entry_port on decision_table_input_entry(input_port_id);
