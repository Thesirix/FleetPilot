-- Savings module: garages, reference tariff, garage quotes analysed line by line, negotiation and invoices.

-- ------------------------------------------------------------------ garages

create table garage (
    id            uuid primary key,
    code          varchar(20)  not null unique,
    name          varchar(120) not null,
    network       varchar(20)  not null,
    brand         varchar(40),
    siret         char(14)     not null unique,
    address       varchar(160) not null,
    postal_code   varchar(10)  not null,
    city          varchar(80)  not null,
    region        varchar(4)   not null,
    latitude      numeric(9, 6),
    longitude     numeric(9, 6),
    email         varchar(160) not null,
    phone         varchar(30),
    hourly_rate   numeric(8, 2) not null check (hourly_rate > 0),
    -- approved by the fleet manager: negotiated rates, quality charter
    approved      boolean      not null,
    approved_since date,
    rating        numeric(2, 1) check (rating between 1 and 5),
    version       bigint       not null default 0,
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

create index ix_garage_region on garage (region, city);

-- ------------------------------------------------------------------ reference tariff

-- Reference price of a part, excluding tax, per vehicle class (car, van, truck).
create table part_reference (
    code        varchar(30)  primary key,
    label       varchar(160) not null,
    family      varchar(20)  not null,
    price_car   numeric(10, 2) not null,
    price_van   numeric(10, 2) not null,
    price_truck numeric(10, 2) not null
);

-- Standard labour time ("temps barème") of an operation, in hours, per vehicle class.
create table labour_operation (
    code            varchar(30)  primary key,
    label           varchar(160) not null,
    labour_category varchar(10)  not null,
    hours_car       numeric(5, 2) not null,
    hours_van       numeric(5, 2) not null,
    hours_truck     numeric(5, 2) not null
);

-- Negotiated hourly rate, excluding tax, per region and labour category. Trucks are serviced in specialised workshops.
create table labour_rate (
    region            varchar(4)   not null,
    labour_category   varchar(10)  not null,
    hourly_rate       numeric(8, 2) not null,
    truck_hourly_rate numeric(8, 2) not null,
    primary key (region, labour_category)
);

-- ------------------------------------------------------------------ vehicles (read model fed by fleet.events)

create table vehicle_ref (
    id           uuid primary key,
    company_id   uuid        not null,
    registration varchar(12) not null unique,
    make         varchar(40),
    model        varchar(60),
    category     varchar(10) not null,
    energy       varchar(20),
    city         varchar(80),
    synced_at    timestamptz not null
);

create index ix_vehicle_ref_company on vehicle_ref (company_id);

-- ------------------------------------------------------------------ quotes

create sequence quote_reference_seq;

create table quote (
    id                uuid primary key,
    reference         varchar(20)  not null unique,
    garage_id         uuid         not null references garage (id),
    vehicle_id        uuid         references vehicle_ref (id),
    company_id        uuid,
    registration      varchar(12)  not null,
    vehicle_category  varchar(10),
    garage_reference  varchar(40),
    mileage           integer      check (mileage >= 0),
    description       varchar(1000) not null,
    channel           varchar(10)  not null,
    status            varchar(20)  not null,
    revision          integer      not null default 1,
    -- amounts excluding tax
    amount            numeric(12, 2) not null,
    initial_amount    numeric(12, 2) not null,
    reference_amount  numeric(12, 2),
    overcharge        numeric(12, 2) not null default 0,
    accepted_amount   numeric(12, 2),
    finding_count     integer      not null default 0,
    auto_approved     boolean      not null default false,
    counter_offer     numeric(12, 2),
    submitted_at      timestamptz  not null,
    analysed_at       timestamptz,
    decided_at        timestamptz,
    decided_by        varchar(120),
    decision_comment  varchar(1000),
    completed_on      date,
    invoice_number    varchar(40),
    invoice_amount    numeric(12, 2),
    invoiced_on       date,
    invoice_disputed  boolean      not null default false,
    version           bigint       not null default 0,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    unique (garage_id, garage_reference)
);

create index ix_quote_status on quote (status, submitted_at desc);
create index ix_quote_company on quote (company_id, submitted_at desc);
create index ix_quote_garage on quote (garage_id, submitted_at desc);
create index ix_quote_vehicle on quote (vehicle_id, submitted_at desc);

create table quote_line (
    id                   uuid primary key,
    quote_id             uuid          not null references quote (id) on delete cascade,
    position             integer       not null,
    kind                 varchar(10)   not null,
    code                 varchar(30),
    label                varchar(200)  not null,
    -- hours for a labour line, units otherwise
    quantity             numeric(8, 2) not null check (quantity > 0),
    -- hourly rate for a labour line
    unit_price           numeric(10, 2) not null check (unit_price >= 0),
    amount               numeric(12, 2) not null,
    reference_quantity   numeric(8, 2),
    reference_unit_price numeric(10, 2),
    reference_amount     numeric(12, 2),
    finding              varchar(30),
    finding_detail       varchar(300),
    unique (quote_id, position)
);

-- Timeline of the quote: submission, analysis, counter-offers, decisions, invoice. Oldest first.
create table quote_history (
    id         uuid primary key,
    quote_id   uuid         not null references quote (id) on delete cascade,
    at         timestamptz  not null,
    party      varchar(10)  not null,
    author     varchar(120) not null,
    action     varchar(30)  not null,
    amount     numeric(12, 2),
    message    varchar(1000)
);

create index ix_quote_history_quote on quote_history (quote_id, at);

-- ------------------------------------------------------------------ messaging

-- Transactional outbox, same structure in every service (see fleet-messaging).
create table outbox_event (
    id             uuid primary key,
    position       bigint generated always as identity,
    aggregate_type varchar(40)  not null,
    aggregate_id   uuid         not null,
    event_type     varchar(60)  not null,
    topic          varchar(80)  not null,
    payload        jsonb        not null,
    occurred_at    timestamptz  not null,
    published_at   timestamptz,
    attempts       integer      not null default 0,
    last_error     varchar(500)
);

create index ix_outbox_pending on outbox_event (position) where published_at is null;

-- Events already handled by a consumer of this service (Kafka delivers at least once).
create table processed_event (
    event_id     uuid        not null,
    consumer     varchar(60) not null,
    processed_at timestamptz not null default now(),
    primary key (event_id, consumer)
);
