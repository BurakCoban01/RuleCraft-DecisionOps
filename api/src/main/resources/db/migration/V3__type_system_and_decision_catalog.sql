create table type_definition (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    type_key varchar(40) not null,
    name varchar(160) not null,
    description varchar(600),
    kind varchar(24) not null,
    item_builtin_type varchar(24),
    item_custom_type_id uuid references type_definition(id),
    created_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_type_definition_project_key unique (project_id, type_key),
    constraint ck_type_definition_key check (type_key = upper(type_key) and length(type_key) between 2 and 40 and type_key not like '% %'),
    constraint ck_type_definition_kind check (kind in ('OBJECT', 'ENUM', 'LIST')),
    constraint ck_type_definition_item_builtin check (item_builtin_type is null or item_builtin_type in ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATE_TIME')),
    constraint ck_type_definition_list_item check (
        (kind = 'LIST' and ((item_builtin_type is not null and item_custom_type_id is null)
            or (item_builtin_type is null and item_custom_type_id is not null)))
        or (kind <> 'LIST' and item_builtin_type is null and item_custom_type_id is null)
    )
);

create index ix_type_definition_tenant_project on type_definition(tenant_id, project_id);

create table type_field (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    type_definition_id uuid not null references type_definition(id) on delete cascade,
    field_key varchar(60) not null,
    label varchar(160) not null,
    builtin_type varchar(24),
    custom_type_id uuid references type_definition(id),
    required boolean not null,
    position integer not null,
    constraint uq_type_field_key unique (type_definition_id, field_key),
    constraint uq_type_field_position unique (type_definition_id, position),
    constraint ck_type_field_key check (length(field_key) between 1 and 60 and field_key not like '% %'),
    constraint ck_type_field_builtin check (builtin_type is null or builtin_type in ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATE_TIME')),
    constraint ck_type_field_ref check ((builtin_type is not null and custom_type_id is null)
        or (builtin_type is null and custom_type_id is not null)),
    constraint ck_type_field_position check (position >= 0)
);

create index ix_type_field_custom_ref on type_field(custom_type_id);

create table type_enum_value (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    type_definition_id uuid not null references type_definition(id) on delete cascade,
    value_key varchar(80) not null,
    label varchar(160) not null,
    position integer not null,
    constraint uq_type_enum_value_key unique (type_definition_id, value_key),
    constraint uq_type_enum_value_position unique (type_definition_id, position),
    constraint ck_type_enum_value_key check (length(value_key) between 1 and 80),
    constraint ck_type_enum_value_position check (position >= 0)
);

create table decision_service (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    service_key varchar(40) not null,
    name varchar(160) not null,
    description varchar(600),
    status varchar(24) not null,
    created_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_decision_service_project_key unique (project_id, service_key),
    constraint ck_decision_service_key check (service_key = upper(service_key) and length(service_key) between 2 and 40 and service_key not like '% %'),
    constraint ck_decision_service_status check (status in ('ACTIVE', 'ARCHIVED'))
);

create index ix_decision_service_tenant_project on decision_service(tenant_id, project_id);

create table decision_service_field (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    decision_service_id uuid not null references decision_service(id) on delete cascade,
    direction varchar(16) not null,
    field_key varchar(60) not null,
    label varchar(160) not null,
    builtin_type varchar(24),
    custom_type_id uuid references type_definition(id),
    required boolean not null,
    position integer not null,
    constraint uq_decision_service_field_key unique (decision_service_id, direction, field_key),
    constraint uq_decision_service_field_position unique (decision_service_id, direction, position),
    constraint ck_decision_service_field_direction check (direction in ('INPUT', 'OUTPUT')),
    constraint ck_decision_service_field_builtin check (builtin_type is null or builtin_type in ('TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATE_TIME')),
    constraint ck_decision_service_field_ref check ((builtin_type is not null and custom_type_id is null)
        or (builtin_type is null and custom_type_id is not null)),
    constraint ck_decision_service_field_position check (position >= 0)
);

create index ix_decision_service_field_custom_ref on decision_service_field(custom_type_id);
