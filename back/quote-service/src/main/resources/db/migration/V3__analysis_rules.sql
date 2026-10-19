-- Thresholds of the quote analysis and approval limits, set by the platform administrators from the portal.
create table analysis_rules (
    id                     smallint primary key check (id = 1),
    part_tolerance         numeric(5, 4)  not null,
    time_tolerance         numeric(5, 4)  not null,
    rate_tolerance         numeric(5, 4)  not null,
    supplies_share         numeric(5, 4)  not null,
    supplies_minimum       numeric(10, 2) not null,
    negotiation_share      numeric(5, 4)  not null,
    negotiation_minimum    numeric(10, 2) not null,
    auto_approval_limit    numeric(10, 2) not null,
    manager_approval_limit numeric(10, 2) not null,
    updated_at             timestamptz    not null default now(),
    updated_by             varchar(160)   not null default 'FleetPilot',
    version                bigint         not null default 0
);

insert into analysis_rules (id, part_tolerance, time_tolerance, rate_tolerance, supplies_share, supplies_minimum,
                            negotiation_share, negotiation_minimum, auto_approval_limit, manager_approval_limit)
values (1, 0.10, 0.15, 0.10, 0.06, 15.00, 0.01, 5.00, 300.00, 5000.00);
