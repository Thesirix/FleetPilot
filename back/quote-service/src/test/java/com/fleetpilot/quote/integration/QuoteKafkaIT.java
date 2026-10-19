package com.fleetpilot.quote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.fleetpilot.common.event.EventEnvelope;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.messaging.OutboxRelay;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** The quote service on a real Kafka broker: it follows the fleet events and publishes the quote events. */
@SpringBootTest(properties = {
    "fleetpilot.outbox.relay.enabled=true",
    "fleetpilot.outbox.relay.interval=600000",
    "fleetpilot.demo-data.enabled=false"
})
@DisplayName("Quote service on Kafka")
class QuoteKafkaIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private OutboxRelay relay;

    @Test
    void a_vehicle_created_in_the_fleet_is_copied_once_even_when_the_event_is_delivered_twice() throws Exception {
        UUID vehicle = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        EventEnvelope<Object> created = EventEnvelope.of(EventTypes.VEHICLE_CREATED, Instant.now(), "vehicle-service",
                company, null, Map.of("vehicleId", vehicle.toString(), "registration", "KA-123-FK", "category", "VAN",
                        "energy", "ELECTRIC", "make", "Renault", "model", "Kangoo E-Tech", "mileage", 12));
        String json = jsonMapper.writeValueAsString(created);
        kafka.send(EventTypes.TOPIC_FLEET, vehicle.toString(), json).get();
        kafka.send(EventTypes.TOPIC_FLEET, vehicle.toString(), json).get();

        waitUntil(() -> count("select count(*) from processed_event where event_id = '" + created.eventId() + "'") == 1);
        assertThat(jdbc.sql("select registration, category, company_id from vehicle_ref where id = :id")
                .param("id", vehicle)
                .query((rs, row) -> List.of(rs.getString(1), rs.getString(2), rs.getObject(3, UUID.class)))
                .single()).containsExactly("KA-123-FK", "VAN", company);

        EventEnvelope<Object> renamed = EventEnvelope.of(EventTypes.VEHICLE_UPDATED, Instant.now(), "vehicle-service",
                company, null, Map.of("vehicleId", vehicle.toString(), "registration", "KA-124-FK"));
        kafka.send(EventTypes.TOPIC_FLEET, vehicle.toString(), jsonMapper.writeValueAsString(renamed)).get();
        waitUntil(() -> count("select count(*) from vehicle_ref where registration = 'KA-124-FK'") == 1);
    }

    @Test
    void a_message_that_cannot_be_read_goes_to_the_dead_letter_topic() throws Exception {
        kafka.send(EventTypes.TOPIC_FLEET, "broken", "not json").get();
        List<ConsumerRecord<String, String>> dead = consume(EventTypes.TOPIC_FLEET + ".DLT", 1);
        assertThat(dead).extracting(ConsumerRecord::value).contains("not json");
    }

    @Test
    void quote_events_written_to_the_outbox_reach_kafka() throws Exception {
        UUID aggregate = UUID.randomUUID();
        jdbc.sql("""
                insert into outbox_event (id, aggregate_type, aggregate_id, event_type, topic, payload, occurred_at)
                values (:id, 'quote', :aggregate, 'quote.accepted', 'quote.events',
                        cast('{"type":"quote.accepted","payload":{"reference":"DEV-2026-000001"}}' as jsonb), now())""")
                .param("id", UUID.randomUUID()).param("aggregate", aggregate).update();

        assertThat(relay.relay()).isEqualTo(1);
        List<ConsumerRecord<String, String>> records = consume(EventTypes.TOPIC_QUOTES, 1);
        assertThat(records).anySatisfy(r -> {
            assertThat(r.key()).isEqualTo(aggregate.toString());
            assertThat(JsonPath.<String>read(r.value(), "$.payload.reference")).isEqualTo("DEV-2026-000001");
        });
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition reached in time").isLessThan(deadline);
            Thread.sleep(200);
        }
    }

    private static List<ConsumerRecord<String, String>> consume(String topic, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "quote-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 30_000;
            while (records.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }
}
