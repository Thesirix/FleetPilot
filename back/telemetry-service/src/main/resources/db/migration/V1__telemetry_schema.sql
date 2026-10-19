-- Connected vehicles: live state, sampled positions, trips, daily usage, trouble codes and alerts.

-- Last known state of each connected vehicle (one row per vehicle, updated in place).
create table vehicle_state (
    vehicle_id       uuid primary key,
    company_id       uuid        not null,
    registration     varchar(12) not null,
    latitude         numeric(9, 6) not null,
    longitude        numeric(9, 6) not null,
    speed_kmh        integer     not null,
    heading          integer     not null,
    ignition         boolean     not null,
    battery_percent  integer,
    odometer_km      integer,
    last_position_at timestamptz not null,
    last_moving_at   timestamptz
);

create index ix_vehicle_state_company on vehicle_state (company_id);

-- Positions kept for the tracks: one every 30 seconds per vehicle, and each start and stop. Purged after 7 days.
create table position_history (
    vehicle_id uuid          not null,
    at         timestamptz   not null,
    latitude   numeric(9, 6) not null,
    longitude  numeric(9, 6) not null,
    speed_kmh  integer       not null,
    ignition   boolean       not null,
    primary key (vehicle_id, at)
);

create index ix_position_history_at on position_history (at);

-- A trip: from the ignition on to the ignition off.
create table trip (
    id            uuid primary key,
    vehicle_id    uuid          not null,
    company_id    uuid          not null,
    started_at    timestamptz   not null,
    ended_at      timestamptz   not null,
    start_lat     numeric(9, 6) not null,
    start_lon     numeric(9, 6) not null,
    end_lat       numeric(9, 6) not null,
    end_lon       numeric(9, 6) not null,
    distance_km   numeric(8, 1) not null,
    max_speed     integer       not null,
    average_speed integer       not null,
    idle_minutes  integer       not null
);

create index ix_trip_vehicle on trip (vehicle_id, started_at desc);

-- Usage per vehicle and day: the basis of the usage analysis (and of the energy transition study).
create table daily_usage (
    vehicle_id      uuid          not null,
    company_id      uuid          not null,
    day             date          not null,
    distance_km     numeric(8, 1) not null,
    trips           integer       not null,
    longest_trip_km numeric(8, 1) not null,
    driving_minutes integer       not null,
    max_speed       integer       not null,
    primary key (vehicle_id, day)
);

create index ix_daily_usage_company on daily_usage (company_id, day);

-- Trouble codes reported by the vehicles.
create table fault (
    id           uuid primary key,
    vehicle_id   uuid         not null,
    company_id   uuid         not null,
    registration varchar(12)  not null,
    at           timestamptz  not null,
    code         varchar(10)  not null,
    description  varchar(200) not null,
    severity     varchar(10)  not null
);

create index ix_fault_vehicle on fault (vehicle_id, at desc);

-- Alerts raised by the rules. At most one alert not resolved per vehicle and type: repeats are counted on it.
create table alert (
    id              uuid primary key,
    vehicle_id      uuid          not null,
    company_id      uuid          not null,
    registration    varchar(12)   not null,
    type            varchar(20)   not null,
    severity        varchar(10)   not null,
    message         varchar(300)  not null,
    value           numeric(10, 1),
    latitude        numeric(9, 6),
    longitude       numeric(9, 6),
    status          varchar(15)   not null,
    raised_at       timestamptz   not null,
    last_seen_at    timestamptz   not null,
    occurrences     integer       not null default 1,
    handled_by      varchar(120),
    acknowledged_at timestamptz,
    resolved_at     timestamptz,
    comment         varchar(500),
    version         bigint        not null default 0
);

create unique index ux_alert_open on alert (vehicle_id, type) where status <> 'RESOLVED';
create index ix_alert_status on alert (status, raised_at desc);
create index ix_alert_company on alert (company_id, raised_at desc);

-- Reference data of the fleet module, refreshed every hour: category of each vehicle, mileage of its next service.
create table vehicle_reference (
    vehicle_id          uuid primary key,
    category            varchar(10) not null,
    energy              varchar(20),
    service_due_mileage integer,
    refreshed_at        timestamptz not null
);

-- Transactional outbox and consumed events, same structure in every service (see fleet-messaging).
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

create table processed_event (
    event_id     uuid        not null,
    consumer     varchar(60) not null,
    processed_at timestamptz not null default now(),
    primary key (event_id, consumer)
);
