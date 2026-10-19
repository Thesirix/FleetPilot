package com.fleetpilot.messaging;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays pending outbox events to Kafka, oldest first, keyed by aggregate id so that the events of one aggregate are
 * consumed in order. A batch is sent in one go and then acknowledged event by event.
 *
 * <p>Delivery is "at least once": if Kafka fails in the middle of a batch, the unacknowledged events are sent again
 * at the next run, so consumers deduplicate with the {@code eventId} of the envelope ({@link ProcessedEvents}).
 * {@code FOR UPDATE SKIP LOCKED} lets several instances of a service relay in parallel without sending an event twice.
 */
public class OutboxRelay {

    /** Kafka header carrying the event type, so consumers can filter without parsing the payload. */
    public static final String EVENT_TYPE_HEADER = "event-type";

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 500;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final int MAX_ERROR_LENGTH = 500;

    private record Pending(UUID id, String topic, UUID aggregateId, String eventType, String payload) {
    }

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, Clock clock) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${fleetpilot.outbox.relay.interval:1000}")
    @Transactional
    public int relay() {
        List<Pending> batch = jdbc.query("""
                select id, topic, aggregate_id, event_type, payload::text as payload from outbox_event
                where published_at is null
                order by position
                limit ?
                for update skip locked""",
                (rs, row) -> new Pending(rs.getObject("id", UUID.class), rs.getString("topic"),
                        rs.getObject("aggregate_id", UUID.class), rs.getString("event_type"), rs.getString("payload")),
                BATCH_SIZE);
        if (batch.isEmpty()) {
            return 0;
        }
        List<CompletableFuture<SendResult<String, String>>> sends = batch.stream().map(this::send).toList();
        List<UUID> published = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            Pending event = batch.get(i);
            try {
                sends.get(i).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                published.add(event.id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                markFailed(event.id(), "interrupted");
                break;
            } catch (ExecutionException | TimeoutException e) {
                LOG.warn("Kafka unavailable, {} events will be retried: {}", batch.size() - published.size(),
                        e.getMessage());
                markFailed(event.id(), e.getMessage());
                break;
            }
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.batchUpdate("update outbox_event set published_at = ?, attempts = attempts + 1, last_error = null "
                + "where id = ?", published.stream().map(id -> new Object[] {now, id}).toList());
        LOG.debug("Relayed {} of {} events to Kafka", published.size(), batch.size());
        return published.size();
    }

    /** Number of events not yet sent to Kafka (exposed for monitoring and tests). */
    public long pending() {
        Long count = jdbc.queryForObject("select count(*) from outbox_event where published_at is null", Long.class);
        return count == null ? 0 : count;
    }

    private void markFailed(UUID id, String error) {
        String message = error == null ? "unknown" : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        jdbc.update("update outbox_event set attempts = attempts + 1, last_error = ? where id = ?", message, id);
    }

    private CompletableFuture<SendResult<String, String>> send(Pending event) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(event.topic(),
                event.aggregateId().toString(), event.payload());
        producerRecord.headers().add(EVENT_TYPE_HEADER, event.eventType().getBytes(StandardCharsets.UTF_8));
        return kafka.send(producerRecord);
    }
}
