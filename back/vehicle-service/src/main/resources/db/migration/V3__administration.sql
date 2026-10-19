-- Administration of the client companies: postal address, contact at the client, activation, optimistic locking.
alter table company
    add column address       varchar(200),
    add column postal_code   varchar(5) check (postal_code ~ '^[0-9]{5}$'),
    add column contact_name  varchar(120),
    add column contact_email varchar(160),
    add column contact_phone varchar(30),
    add column active        boolean not null default true,
    add column version       bigint  not null default 0;

alter table site add column version bigint not null default 0;
alter table cost_center add column version bigint not null default 0;

-- Who did what in the platform: one line per business action, written in the same transaction as the action.
create table audit_entry (
    id           uuid primary key,
    occurred_at  timestamptz  not null,
    actor        varchar(160) not null,
    actor_role   varchar(30),
    company_id   uuid,
    action       varchar(60)  not null,
    target_type  varchar(30)  not null,
    target_id    varchar(60),
    target_label varchar(200),
    details      varchar(500)
);
create index ix_audit_entry_time on audit_entry (occurred_at desc);
create index ix_audit_entry_company on audit_entry (company_id, occurred_at desc);
