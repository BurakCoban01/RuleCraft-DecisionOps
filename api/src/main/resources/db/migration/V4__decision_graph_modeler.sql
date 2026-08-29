create table decision_graph (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    validation_status varchar(16) not null,
    created_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_decision_graph_service unique (decision_service_id),
    constraint ck_decision_graph_status check (validation_status in ('VALID', 'INVALID'))
);

create index ix_decision_graph_tenant_project on decision_graph(tenant_id, project_id);

create table graph_node (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    graph_id uuid not null references decision_graph(id) on delete cascade,
    node_key varchar(60) not null,
    name varchar(160) not null,
    kind varchar(16) not null,
    service_field_key varchar(60),
    position_x integer not null,
    position_y integer not null,
    constraint uq_graph_node_key unique (graph_id, node_key),
    constraint ck_graph_node_kind check (kind in ('INPUT', 'DECISION', 'OUTPUT')),
    constraint ck_graph_node_contract check (
        (kind = 'DECISION' and service_field_key is null)
        or (kind in ('INPUT', 'OUTPUT') and service_field_key is not null)
    ),
    constraint ck_graph_node_position check (position_x between 0 and 4000 and position_y between 0 and 4000)
);

create index ix_graph_node_graph on graph_node(graph_id);

create table graph_port (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    graph_id uuid not null references decision_graph(id) on delete cascade,
    node_id uuid not null references graph_node(id) on delete cascade,
    direction varchar(16) not null,
    port_key varchar(60) not null,
    label varchar(160) not null,
    builtin_type varchar(24),
    custom_type_id uuid references type_definition(id),
    required boolean not null,
    position integer not null,
    constraint uq_graph_port_key unique (node_id, direction, port_key),
    constraint uq_graph_port_position unique (node_id, direction, position),
    constraint ck_graph_port_direction check (direction in ('INPUT', 'OUTPUT')),
    constraint ck_graph_port_builtin check (builtin_type is null or builtin_type in ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATE_TIME')),
    constraint ck_graph_port_type check ((builtin_type is not null and custom_type_id is null)
        or (builtin_type is null and custom_type_id is not null)),
    constraint ck_graph_port_position check (position >= 0)
);

create index ix_graph_port_graph on graph_port(graph_id);
create index ix_graph_port_custom_type on graph_port(custom_type_id);

create table graph_edge (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    graph_id uuid not null references decision_graph(id) on delete cascade,
    source_port_id uuid not null references graph_port(id) on delete cascade,
    target_port_id uuid not null references graph_port(id) on delete cascade,
    constraint uq_graph_edge_target unique (graph_id, target_port_id),
    constraint uq_graph_edge_pair unique (graph_id, source_port_id, target_port_id),
    constraint ck_graph_edge_distinct check (source_port_id <> target_port_id)
);

create index ix_graph_edge_graph on graph_edge(graph_id);
