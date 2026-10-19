-- Journal of the integration bus: every file or message received from a partner, what became of it, and why.

create table exchange (
    id            uuid         primary key,
    flow          varchar(20)  not null,
    partner       varchar(120),
    source        varchar(255) not null,
    checksum      char(64),
    content_type  varchar(60),
    payload       text,
    received_at   timestamptz  not null,
    completed_at  timestamptz,
    status        varchar(12)  not null,
    records       integer      not null default 0,
    accepted      integer      not null default 0,
    rejected      integer      not null default 0,
    summary       varchar(500),
    result_ref    varchar(60),
    replay_of     uuid         references exchange (id)
);

create index exchange_received_idx on exchange (received_at desc);
create index exchange_flow_status_idx on exchange (flow, status);

create table exchange_issue (
    id           bigserial    primary key,
    exchange_id  uuid         not null references exchange (id) on delete cascade,
    line         integer,
    reference    varchar(60),
    severity     varchar(10)  not null,
    code         varchar(40)  not null,
    message      varchar(500) not null
);

create index exchange_issue_exchange_idx on exchange_issue (exchange_id);

-- Idempotent consumer of the routes (Camel IdempotentRepository): a file already handled is not handled twice
create table processed_file (
    message_key   varchar(120) primary key,
    exchange_id   uuid         not null,
    processed_at  timestamptz  not null
);

-- Claims declared to the insurer, followed until the insurer settles them
create table insurer_claim (
    incident_id        uuid         primary key,
    incident_reference varchar(30)  not null,
    claim_number       varchar(30)  not null unique,
    company_id         uuid,
    registration       varchar(12),
    insurer_status     varchar(20)  not null,
    fleet_status       varchar(20)  not null,
    declared_at        timestamptz  not null,
    last_synced_at     timestamptz  not null
);

-- Fuel card transactions, matched with the fleet and checked
create table fuel_transaction (
    id               uuid          primary key,
    exchange_id      uuid          not null references exchange (id),
    provider         varchar(40)   not null,
    transaction_ref  varchar(40)   not null,
    card_number      varchar(24)   not null,
    occurred_at      timestamptz   not null,
    station          varchar(120)  not null,
    city             varchar(80),
    product          varchar(20)   not null,
    litres           numeric(8, 2) not null,
    unit_price       numeric(6, 3) not null,
    amount           numeric(10, 2) not null,
    odometer         integer,
    registration     varchar(12)   not null,
    vehicle_id       uuid,
    company_id       uuid,
    energy           varchar(20),
    anomalies        varchar(200),
    unique (provider, transaction_ref)
);

create index fuel_transaction_occurred_idx on fuel_transaction (occurred_at desc);
create index fuel_transaction_vehicle_idx on fuel_transaction (vehicle_id, occurred_at);
create index fuel_transaction_company_idx on fuel_transaction (company_id, occurred_at);

-- The insurer's own records: the simulated partner keeps its claims here
create table simulated_insurer_claim (
    claim_number     varchar(30)   primary key,
    policy_number    varchar(30)   not null,
    incident_ref     varchar(30)   not null unique,
    registration     varchar(12)   not null,
    incident_type    varchar(20)   not null,
    occurred_at      timestamptz   not null,
    injuries         boolean       not null,
    declared_at      timestamptz   not null
);
