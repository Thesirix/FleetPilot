-- Every e-mail sent by FleetPilot, with its rendering, so that it can be read again and sent again.

create table notification (
    id               uuid primary key,
    -- idempotency key of the request: a request received twice is sent once
    request_id       varchar(120) not null unique,
    template         varchar(40)  not null,
    recipient        varchar(200) not null,
    recipient_name   varchar(120),
    subject          varchar(250),
    status           varchar(10)  not null,
    attempts         integer      not null default 0,
    last_error       varchar(500),
    company_id       uuid,
    related_type     varchar(30),
    related_id       varchar(60),
    attachments      varchar(300),
    -- the request as received (template variables included), to send it again
    request          jsonb        not null,
    body_html        text,
    message_id       varchar(250),
    -- set when a consumer takes the notification, so that two copies of a message are never sent twice
    claimed_at       timestamptz,
    created_at       timestamptz  not null default now(),
    sent_at          timestamptz,
    version          bigint       not null default 0
);

create index ix_notification_status on notification (status, created_at desc);
create index ix_notification_template on notification (template, created_at desc);
create index ix_notification_related on notification (related_type, related_id);

-- Kafka events already turned into notifications (at-least-once delivery).
create table processed_event (
    event_id     uuid        not null,
    consumer     varchar(60) not null,
    processed_at timestamptz not null default now(),
    primary key (event_id, consumer)
);

-- Required by the shared messaging module; this service publishes no Kafka event.
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
