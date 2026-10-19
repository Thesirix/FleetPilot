package com.fleetpilot.messaging;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumption: Kafka delivers "at least once", so a consumer records the id of every event it has handled
 * in the {@code processed_event} table, in the same transaction as its own changes. A redelivered event is skipped.
 */
public class ProcessedEvents {

    private final JdbcTemplate jdbc;

    public ProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records the event for this consumer.
     *
     * @return true the first time, false when the event was already handled (it must then be ignored)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstDelivery(UUID eventId, String consumer) {
        return jdbc.update("insert into processed_event (event_id, consumer) values (?, ?) on conflict do nothing",
                eventId, consumer) == 1;
    }
}
