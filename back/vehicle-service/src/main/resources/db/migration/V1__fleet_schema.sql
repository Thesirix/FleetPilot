-- FleetPilot vehicle-service: fleet of the client companies managed on the platform.
-- Every row belongs to a client company (company_id), which is the tenant isolation key.

create table company (
    id          uuid primary key,
    name        varchar(120) not null unique,
    legal_name  varchar(160) not null,
    siren       varchar(9)   not null unique check (siren ~ '^[0-9]{9}$'),
    segment     varchar(20)  not null check (segment in ('LARGE_ACCOUNT', 'PUBLIC_SECTOR', 'SME')),
    city        varchar(80)  not null,
    created_at  timestamptz  not null default now()
);

create table site (
    id          uuid primary key,
    company_id  uuid          not null references company (id),
    name        varchar(120)  not null,
    address     varchar(200)  not null,
    postal_code varchar(5)    not null,
    city        varchar(80)   not null,
    latitude    numeric(9, 6) not null,
    longitude   numeric(9, 6) not null,
    unique (company_id, name)
);

create table cost_center (
    id          uuid primary key,
    company_id  uuid         not null references company (id),
    code        varchar(20)  not null,
    label       varchar(120) not null,
    unique (company_id, code)
);

create table driver (
    id                 uuid primary key,
    company_id         uuid         not null references company (id),
    site_id            uuid references site (id),
    employee_number    varchar(20)  not null,
    first_name         varchar(60)  not null,
    last_name          varchar(60)  not null,
    email              varchar(160) not null unique,
    phone              varchar(20),
    license_number     varchar(20)  not null,
    license_categories varchar(40)  not null,
    license_issued_on  date         not null,
    license_expires_on date,
    active             boolean      not null default true,
    version            bigint       not null default 0,
    created_at         timestamptz  not null default now(),
    updated_at         timestamptz  not null default now(),
    unique (company_id, employee_number)
);

create index ix_driver_company on driver (company_id, active);
create index ix_driver_name on driver (lower(last_name), lower(first_name));

create table vehicle (
    id                      uuid primary key,
    company_id              uuid        not null references company (id),
    site_id                 uuid references site (id),
    cost_center_id          uuid references cost_center (id),
    registration            varchar(9)  not null unique,
    vin                     varchar(17) not null unique,
    category                varchar(10) not null check (category in ('CAR', 'VAN', 'TRUCK', 'MACHINE')),
    make                    varchar(40) not null,
    model                   varchar(60) not null,
    trim_level              varchar(80),
    energy                  varchar(20) not null,
    -- 0 is the green "Crit'Air E" sticker (electric and hydrogen vehicles)
    crit_air                integer     not null check (crit_air between 0 and 5),
    co2_g_km                integer     not null check (co2_g_km >= 0),
    fiscal_power            integer,
    first_registration_date date        not null,
    acquisition             varchar(10) not null check (acquisition in ('LEASE', 'OWNED')),
    mileage                 integer     not null check (mileage >= 0),
    mileage_read_at         timestamptz not null,
    status                  varchar(20) not null,
    version                 bigint      not null default 0,
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now()
);

create index ix_vehicle_company_status on vehicle (company_id, status);
create index ix_vehicle_site on vehicle (site_id);
create index ix_vehicle_search on vehicle (lower(make), lower(model));

create table mileage_reading (
    id         uuid primary key,
    vehicle_id uuid        not null references vehicle (id),
    mileage    integer     not null check (mileage >= 0),
    read_at    timestamptz not null,
    source     varchar(12) not null
);

create index ix_mileage_reading_vehicle on mileage_reading (vehicle_id, read_at desc);

create table assignment (
    id            uuid primary key,
    vehicle_id    uuid        not null references vehicle (id),
    driver_id     uuid        not null references driver (id),
    kind          varchar(12) not null check (kind in ('PERMANENT', 'TEMPORARY')),
    start_date    date        not null,
    end_date      date,
    start_mileage integer     not null,
    end_mileage   integer,
    created_at    timestamptz not null default now(),
    check (end_date is null or end_date >= start_date)
);

-- A vehicle has at most one current driver; a driver has at most one permanent vehicle.
create unique index ux_assignment_current_vehicle on assignment (vehicle_id) where end_date is null;
create unique index ux_assignment_current_driver on assignment (driver_id) where end_date is null and kind = 'PERMANENT';
create index ix_assignment_driver on assignment (driver_id, start_date desc);

create table lease_contract (
    id                   uuid primary key,
    vehicle_id           uuid          not null references vehicle (id),
    lessor               varchar(40)   not null,
    contract_number      varchar(30)   not null unique,
    start_date           date          not null,
    duration_months      integer       not null check (duration_months between 6 and 84),
    end_date             date          not null,
    contractual_mileage  integer       not null check (contractual_mileage > 0),
    start_mileage        integer       not null default 0,
    monthly_rent         numeric(10, 2) not null check (monthly_rent > 0),
    maintenance_included boolean       not null,
    tyres_included       boolean       not null,
    replacement_vehicle  boolean       not null,
    status               varchar(10)   not null check (status in ('ACTIVE', 'ENDED')),
    ended_on             date,
    -- set when the daily sweep warned that the mileage will exceed the contract, so it warns only once
    overrun_alerted_on   date,
    version              bigint        not null default 0,
    created_at           timestamptz   not null default now()
);

create unique index ux_contract_active_vehicle on lease_contract (vehicle_id) where status = 'ACTIVE';
create index ix_contract_end on lease_contract (status, end_date);

create table deadline (
    id                uuid primary key,
    vehicle_id        uuid        not null references vehicle (id),
    type              varchar(30) not null,
    due_date          date,
    due_mileage       integer,
    status            varchar(10) not null check (status in ('OPEN', 'DONE', 'CANCELLED')),
    completed_on      date,
    completed_mileage integer,
    -- last alert sent by the daily sweep (D30, D7, OVERDUE), so each level is sent only once
    last_alert        varchar(10),
    created_at        timestamptz not null default now(),
    check (due_date is not null or due_mileage is not null)
);

create unique index ux_deadline_open on deadline (vehicle_id, type) where status = 'OPEN';
create index ix_deadline_due on deadline (status, due_date);

create sequence incident_reference_seq start 1;

create table incident (
    id                   uuid primary key,
    vehicle_id           uuid          not null references vehicle (id),
    driver_id            uuid references driver (id),
    reference            varchar(20)   not null unique,
    type                 varchar(20)   not null,
    occurred_at          timestamptz   not null,
    location             varchar(200)  not null,
    latitude             numeric(9, 6),
    longitude            numeric(9, 6),
    description          varchar(2000) not null,
    third_party_involved boolean       not null,
    injuries             boolean       not null,
    responsibility       varchar(10)   not null,
    estimated_cost       numeric(10, 2),
    status               varchar(15)   not null,
    closed_on            date,
    version              bigint        not null default 0,
    created_at           timestamptz   not null default now(),
    updated_at           timestamptz   not null default now()
);

create index ix_incident_vehicle on incident (vehicle_id, occurred_at desc);
create index ix_incident_status on incident (status, occurred_at desc);

-- Transactional outbox: events are written in the same transaction as the business change,
-- then relayed to Kafka. No event is lost and none is published for a rolled back change.
create table outbox_event (
    id             uuid primary key,
    -- insertion order: events are relayed in the exact order they were written, even within one millisecond
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
