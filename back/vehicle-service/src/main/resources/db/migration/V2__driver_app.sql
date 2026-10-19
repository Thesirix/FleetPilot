-- What the drivers send from the mobile app. The ids are generated on the phone: a submission replayed after a network
-- failure (offline queue) finds its own row and is not recorded twice.

create table fuel_entry (
    id         uuid primary key,
    vehicle_id uuid          not null references vehicle (id),
    driver_id  uuid          not null references driver (id),
    filled_at  timestamptz   not null,
    -- litres of fuel, or kWh for a charge
    kind       varchar(12)   not null check (kind in ('FUEL', 'CHARGE')),
    quantity   numeric(7, 2) not null check (quantity > 0),
    amount     numeric(8, 2) not null check (amount >= 0),
    mileage    integer       not null check (mileage >= 0),
    station    varchar(120),
    payment    varchar(12)   not null check (payment in ('FUEL_CARD', 'PERSONAL')),
    created_at timestamptz   not null default now()
);

create index ix_fuel_entry_vehicle on fuel_entry (vehicle_id, filled_at desc);
create index ix_fuel_entry_driver on fuel_entry (driver_id, filled_at desc);

-- Check of the vehicle when a driver takes it, gives it back, or once a month.
create table inspection (
    id           uuid primary key,
    vehicle_id   uuid        not null references vehicle (id),
    driver_id    uuid        not null references driver (id),
    kind         varchar(10) not null check (kind in ('PICKUP', 'RETURN', 'PERIODIC')),
    performed_at timestamptz not null,
    mileage      integer     not null check (mileage >= 0),
    fuel_level   integer     not null check (fuel_level between 0 and 100),
    remarks      varchar(1000),
    created_at   timestamptz not null default now()
);

create index ix_inspection_vehicle on inspection (vehicle_id, performed_at desc);
create index ix_inspection_driver on inspection (driver_id, performed_at desc);

create table inspection_item (
    inspection_id uuid         not null references inspection (id) on delete cascade,
    item          varchar(20)  not null,
    condition     varchar(10)  not null check (condition in ('OK', 'DAMAGED', 'MISSING')),
    comment       varchar(300),
    primary key (inspection_id, item)
);

-- Photos of a claim or of an inspection, resized on the phone (about 300 kB each).
create table photo (
    id           uuid primary key,
    owner_type   varchar(12) not null check (owner_type in ('INCIDENT', 'INSPECTION')),
    owner_id     uuid        not null,
    vehicle_id   uuid        not null references vehicle (id),
    content_type varchar(30) not null,
    size_bytes   integer     not null,
    content      bytea       not null,
    created_at   timestamptz not null default now()
);

create index ix_photo_owner on photo (owner_type, owner_id, created_at);

-- Copy of the alerts raised by the connected vehicles (topic telemetry.alerts), so that a driver sees the alerts of
-- their vehicle in the app without this service calling the telemetry service.
create table vehicle_alert (
    id         uuid primary key,
    vehicle_id uuid         not null,
    type       varchar(20)  not null,
    severity   varchar(10)  not null,
    message    varchar(300) not null,
    raised_at  timestamptz  not null
);

create index ix_vehicle_alert_vehicle on vehicle_alert (vehicle_id, raised_at desc);
