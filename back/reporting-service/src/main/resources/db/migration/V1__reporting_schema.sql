-- Reports requested from the portal, built in the background from the ActiveMQ queue, kept for download.

create table report (
    id              uuid primary key,
    type            varchar(30)  not null,
    format          varchar(5)   not null,
    title           varchar(200) not null,
    company_id      uuid,
    period_from     date,
    period_to       date,
    requested_by    varchar(120) not null,
    requester_email varchar(200),
    requester_name  varchar(120),
    status          varchar(10)  not null,
    requested_at    timestamptz  not null,
    started_at      timestamptz,
    completed_at    timestamptz,
    file_name       varchar(200),
    content_type    varchar(120),
    size_bytes      integer,
    row_count       integer,
    error           varchar(500),
    version         bigint       not null default 0
);

-- The files apart from the metadata: listing the reports never loads them.
create table report_file (
    report_id uuid primary key references report (id) on delete cascade,
    content   bytea not null
);

create index ix_report_requester on report (requested_by, requested_at desc);
create index ix_report_status on report (status, requested_at);
