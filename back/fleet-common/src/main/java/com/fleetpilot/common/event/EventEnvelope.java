package com.fleetpilot.common.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Envelope of every business event published on Kafka.
 *
 * <p>The envelope carries the metadata consumers need to process events safely: a unique id for idempotence,
 * the event type and schema version, the client company (tenant) the data belongs to and the correlation id of
 * the request that caused it, so a single user action can be followed across services in Kibana.
 *
 * @param eventId       unique id, consumers use it to ignore duplicates
 * @param type          event type, one of {@link EventTypes}
 * @param version       schema version of the payload
 * @param occurredAt    when the fact happened
 * @param source        name of the producing service
 * @param companyId     client company the event belongs to
 * @param correlationId id of the request that caused the event, may be null for scheduled jobs
 * @param payload       event-specific data
 */
public record EventEnvelope<T>(UUID eventId, String type, int version, Instant occurredAt, String source,
        UUID companyId, String correlationId, T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
    }

    /** Creates a version 1 event with a fresh id. */
    public static <T> EventEnvelope<T> of(String type, Instant occurredAt, String source, UUID companyId,
            String correlationId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), type, 1, occurredAt, source, companyId, correlationId, payload);
    }
}
