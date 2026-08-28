create table platform_installation (
    singleton_key varchar(32) primary key,
    schema_version integer not null,
    installed_at timestamp with time zone not null default current_timestamp,
    constraint ck_platform_installation_singleton check (singleton_key = 'primary'),
    constraint ck_platform_schema_version_positive check (schema_version > 0)
);

insert into platform_installation (singleton_key, schema_version)
values ('primary', 1);
