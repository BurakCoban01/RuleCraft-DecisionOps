create table tenant (
    id uuid primary key,
    slug varchar(64) not null,
    name varchar(160) not null,
    created_at timestamp with time zone not null,
    constraint uq_tenant_slug unique (slug),
    constraint ck_tenant_slug check (slug = lower(slug) and length(slug) between 3 and 64 and slug not like '% %')
);

create table user_account (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    email varchar(254) not null,
    display_name varchar(160) not null,
    password_hash varchar(100) not null,
    tenant_role varchar(32) not null,
    status varchar(24) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_user_tenant_email unique (tenant_id, email),
    constraint ck_user_email_normalized check (email = lower(email)),
    constraint ck_user_tenant_role check (tenant_role in ('ADMIN', 'MEMBER')),
    constraint ck_user_status check (status in ('ACTIVE', 'DISABLED'))
);

create index ix_user_account_tenant on user_account(tenant_id);

create table project (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_key varchar(24) not null,
    name varchar(160) not null,
    description varchar(600),
    created_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_project_tenant_key unique (tenant_id, project_key),
    constraint ck_project_key check (project_key = upper(project_key) and length(project_key) between 2 and 24 and project_key not like '% %')
);

create index ix_project_tenant on project(tenant_id);

create table project_membership (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    project_id uuid not null references project(id) on delete cascade,
    user_id uuid not null references user_account(id),
    role varchar(24) not null,
    created_by uuid not null references user_account(id),
    created_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint uq_project_membership unique (project_id, user_id),
    constraint ck_project_role check (role in ('OWNER', 'EDITOR', 'REVIEWER', 'VIEWER'))
);

create index ix_membership_tenant_user on project_membership(tenant_id, user_id);
create index ix_membership_project on project_membership(project_id);

create table audit_event (
    id uuid primary key,
    tenant_id uuid not null references tenant(id),
    actor_user_id uuid references user_account(id),
    action varchar(80) not null,
    target_type varchar(64) not null,
    target_id varchar(80) not null,
    details_json text not null,
    occurred_at timestamp with time zone not null
);

create index ix_audit_event_tenant_time on audit_event(tenant_id, occurred_at desc);

create table spring_session (
    primary_id char(36) not null,
    session_id char(36) not null,
    creation_time bigint not null,
    last_access_time bigint not null,
    max_inactive_interval integer not null,
    expiry_time bigint not null,
    principal_name varchar(100),
    constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
    session_primary_id char(36) not null,
    attribute_name varchar(200) not null,
    attribute_bytes bytea not null,
    constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
    constraint spring_session_attributes_fk foreign key (session_primary_id) references spring_session(primary_id) on delete cascade
);
