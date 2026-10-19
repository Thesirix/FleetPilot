-- Energy transition of the fleet: the electric models proposed, the low emission zone rules, and the assessment of
-- every vehicle (recomputed every night from the fleet and its real usage).

-- Electric models offered in long term rental, with a realistic range below the WLTP one
create table ev_model (
    code                   varchar(20)   primary key,
    make                   varchar(40)   not null,
    model                  varchar(80)   not null,
    category               varchar(10)   not null,
    segment                varchar(10)   not null,
    battery_kwh            numeric(5, 1) not null,
    wltp_range_km          integer       not null,
    consumption_kwh_100km  numeric(5, 1) not null,
    fast_charge_kw         integer       not null,
    monthly_rent           numeric(8, 2) not null
);

insert into ev_model values
    ('R5-ETECH',   'Renault',         '5 E-Tech 52 kWh',            'CAR',   'CITY',     52.0, 410, 15.2, 100,  389.00),
    ('E-208',      'Peugeot',         'e-208 51 kWh',               'CAR',   'CITY',     51.0, 410, 15.5, 100,  409.00),
    ('MEGANE-ET',  'Renault',         'Mégane E-Tech 60 kWh',       'CAR',   'COMPACT',  60.0, 450, 16.1, 130,  469.00),
    ('E-308SW',    'Peugeot',         'e-308 SW 54 kWh',            'CAR',   'COMPACT',  54.0, 410, 15.8, 100,  489.00),
    ('EV3',        'Kia',             'EV3 81 kWh',                 'CAR',   'COMPACT',  81.0, 600, 15.9, 128,  499.00),
    ('MODEL3',     'Tesla',           'Model 3 Propulsion',         'CAR',   'LARGE',    60.0, 513, 13.2, 170,  529.00),
    ('ENYAQ85',    'Skoda',           'Enyaq 85',                   'CAR',   'LARGE',    82.0, 581, 16.8, 135,  579.00),
    ('KANGOO-ET',  'Renault',         'Kangoo Van E-Tech 45 kWh',   'VAN',   'SMALL',    45.0, 300, 20.0,  80,  459.00),
    ('E-BERLINGO', 'Citroën',         'ë-Berlingo Van 50 kWh',      'VAN',   'SMALL',    50.0, 330, 18.6, 100,  469.00),
    ('TRAFIC-ET',  'Renault',         'Trafic Van E-Tech 52 kWh',   'VAN',   'MEDIUM',   52.0, 297, 24.0,  50,  589.00),
    ('E-EXPERT',   'Peugeot',         'e-Expert 75 kWh',            'VAN',   'MEDIUM',   75.0, 350, 23.4, 100,  629.00),
    ('MASTER-ET',  'Renault',         'Master Van E-Tech 87 kWh',   'VAN',   'LARGE',    87.0, 460, 25.0, 130,  779.00),
    ('ESPRINTER',  'Mercedes-Benz',   'eSprinter 113 kWh',          'VAN',   'LARGE',   113.0, 478, 28.0, 115,  899.00),
    ('ETECH-D',    'Renault Trucks',  'E-Tech D 16 t',              'TRUCK', 'TRUCK',   394.0, 280, 110.0, 135, 2900.00),
    ('FL-ELEC',    'Volvo',           'FL Electric 16 t',           'TRUCK', 'TRUCK',   375.0, 300, 105.0, 150, 3100.00);

-- Low emission zones: the oldest Crit'Air sticker still allowed in a city, and since when. Configurable: local
-- decrees change, the rules are data, not code.
create table zfe_rule (
    city             varchar(80)  primary key,
    zone             varchar(120) not null,
    max_crit_air     integer      not null,
    effective_from   date         not null,
    status           varchar(12)  not null
);

insert into zfe_rule values
    ('Paris',          'ZFE du Grand Paris',              2, '2025-01-01', 'IN_FORCE'),
    ('Nanterre',       'ZFE du Grand Paris',              2, '2025-01-01', 'IN_FORCE'),
    ('Lyon',           'ZFE de la Métropole de Lyon',     2, '2026-01-01', 'IN_FORCE'),
    ('Villeurbanne',   'ZFE de la Métropole de Lyon',     2, '2026-01-01', 'IN_FORCE'),
    ('Marseille',      'ZFE Marseille Provence',          2, '2025-09-01', 'IN_FORCE'),
    ('Aix-en-Provence','ZFE Marseille Provence',          3, '2027-01-01', 'ANNOUNCED'),
    ('Toulouse',       'ZFE de Toulouse Métropole',       3, '2027-01-01', 'ANNOUNCED'),
    ('Bordeaux',       'ZFE de Bordeaux Métropole',       3, '2027-01-01', 'ANNOUNCED');

-- One assessment per vehicle of the fleet
create table assessment (
    vehicle_id             uuid          primary key,
    company_id             uuid          not null,
    company_name           varchar(120),
    registration           varchar(12)   not null,
    make                   varchar(40),
    model                  varchar(80),
    category               varchar(10)   not null,
    energy                 varchar(20)   not null,
    crit_air               integer,
    co2_g_km               integer,
    site_city              varchar(80),
    status                 varchar(24)   not null,
    data_source            varchar(12)   not null,
    reason                 varchar(400),
    annual_km              integer       not null,
    active_days            integer,
    p95_daily_km           numeric(7, 1),
    longest_day_km         numeric(7, 1),
    days_beyond_range      integer,
    required_range_km      integer,
    ev_model_code          varchar(20)   references ev_model (code),
    realistic_range_km     integer,
    current_annual_cost    numeric(10, 2),
    ev_annual_cost         numeric(10, 2),
    annual_saving          numeric(10, 2),
    co2_current_kg         integer,
    co2_ev_kg              integer,
    renewal_date           date,
    zfe_zone               varchar(120),
    zfe_banned_from        date,
    computed_at            timestamptz   not null
);

create index assessment_company_idx on assessment (company_id, status);
create index assessment_renewal_idx on assessment (renewal_date);
