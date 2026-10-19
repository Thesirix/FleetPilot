package com.fleetpilot.vehicle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.messaging.OutboxRelay;
import com.fleetpilot.vehicle.domain.event.DomainEventPublisher;
import com.fleetpilot.vehicle.domain.event.FleetEvents;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** The transactional outbox, relayed to a real Kafka broker. */
@SpringBootTest(properties = {
    "fleetpilot.outbox.relay.enabled=true",
    "fleetpilot.outbox.relay.interval=600000",
    "fleetpilot.sweep.enabled=false"
})
@DisplayName("Outbox relay to Kafka")
class OutboxRelayIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    private DomainEventPublisher publisher;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transactions;

    @Test
    void committed_events_reach_kafka_in_order_with_their_envelope() {
        UUID vehicle = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            publisher.publish(EventTypes.VEHICLE_CREATED, company, "vehicle", vehicle,
                    new FleetEvents.VehicleUpdated(vehicle, "GH-482-KM"));
            publisher.publish(EventTypes.VEHICLE_UPDATED, company, "vehicle", vehicle,
                    new FleetEvents.VehicleUpdated(vehicle, "GH-483-KM"));
        });

        assertThat(relay.relay()).isEqualTo(2);
        assertThat(relay.pending()).isZero();

        List<ConsumerRecord<String, String>> records = consume(2);
        assertThat(records).extracting(ConsumerRecord::key).containsOnly(vehicle.toString());
        assertThat(records).extracting(r -> new String(r.headers().lastHeader(OutboxRelay.EVENT_TYPE_HEADER).value(),
                StandardCharsets.UTF_8)).containsExactly(EventTypes.VEHICLE_CREATED, EventTypes.VEHICLE_UPDATED);
        String envelope = records.getFirst().value();
        assertThat(JsonPath.<String>read(envelope, "$.source")).isEqualTo("vehicle-service");
        assertThat(JsonPath.<String>read(envelope, "$.companyId")).isEqualTo(company.toString());
        assertThat(JsonPath.<String>read(envelope, "$.payload.registration")).isEqualTo("GH-482-KM");
        assertThat(JsonPath.<Integer>read(envelope, "$.version")).isEqualTo(1);
    }

    @Test
    void a_rolled_back_change_publishes_nothing() {
        long before = countEvents();
        try {
            transactions.executeWithoutResult(status -> {
                publisher.publish(EventTypes.VEHICLE_CREATED, UUID.randomUUID(), "vehicle", UUID.randomUUID(),
                        new FleetEvents.VehicleUpdated(UUID.randomUUID(), "GH-484-KM"));
                throw new IllegalStateException("business change failed");
            });
        } catch (IllegalStateException expected) {
            // the transaction is rolled back
        }
        assertThat(countEvents()).isEqualTo(before);
    }

    private long countEvents() {
        return jdbc.queryForObject("select count(*) from outbox_event", Long.class);
    }

    private static List<ConsumerRecord<String, String>> consume(int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(EventTypes.TOPIC_FLEET));
            long deadline = System.currentTimeMillis() + 20_000;
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }
}
