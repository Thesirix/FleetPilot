package com.fleetpilot.messaging;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

import com.fleetpilot.common.event.EventEnvelope;
import com.fleetpilot.common.web.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes business events to the {@code outbox_event} table, in the caller's transaction: an event exists if, and only
 * if, the business change is committed. {@link OutboxRelay} then sends it to Kafka.
 *
 * <p>Every service that publishes events owns an {@code outbox_event} table in its own database (same columns in
 * every service, see the Flyway migrations).
 */
public class OutboxPublisher {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String source;

    public OutboxPublisher(JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock, String source) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.source = source;
    }

    /**
     * @param topic         Kafka topic, one of {@link com.fleetpilot.common.event.EventTypes}
     * @param type          event type
     * @param companyId     client company the event belongs to
     * @param aggregateType kind of the changed aggregate ("vehicle", "quote"...)
     * @param aggregateId   id of the changed aggregate, also the Kafka key, which keeps the order per aggregate
     * @param payload       event-specific data, serialized to JSON
     * @return the envelope written to the outbox
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public EventEnvelope<Object> publish(String topic, String type, UUID companyId, String aggregateType,
            UUID aggregateId, Object payload) {
        EventEnvelope<Object> envelope = EventEnvelope.of(type, clock.instant(), source, companyId,
                MDC.get(CorrelationIdFilter.MDC_KEY), payload);
        jdbc.update("""
                insert into outbox_event (id, aggregate_type, aggregate_id, event_type, topic, payload, occurred_at)
                values (?, ?, ?, ?, ?, cast(? as jsonb), ?)""",
                envelope.eventId(), aggregateType, aggregateId, type, topic, jsonMapper.writeValueAsString(envelope),
                Timestamp.from(envelope.occurredAt()));
        return envelope;
    }

    public String source() {
        return source;
    }
}
