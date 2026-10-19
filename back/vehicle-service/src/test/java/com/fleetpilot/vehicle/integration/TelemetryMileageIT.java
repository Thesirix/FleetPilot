package com.fleetpilot.vehicle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
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

/** The odometers of the connected vehicles, read from a real Kafka broker into the register. */
@SpringBootTest(properties = {
    "fleetpilot.demo-data.enabled=true",
    "fleetpilot.outbox.relay.enabled=false",
    "fleetpilot.sweep.enabled=false",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DisplayName("Odometers from telemetry")
class TelemetryMileageIT {

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
    private JsonMapper json;
    @Autowired
    private JdbcClient jdbc;

    private record Current(UUID id, UUID companyId, String registration, int mileage) {
    }

    @Test
    void the_register_follows_the_odometer_every_twenty_kilometres() throws Exception {
        Current vehicle = jdbc.sql("""
                select id, company_id, registration, mileage from vehicle where status = 'ACTIVE'
                order by registration limit 1""").query(Current.class).single();
        int start = vehicle.mileage();
        Instant now = Instant.now();

        send(vehicle, now.minusSeconds(300), start + 5);
        send(vehicle, now.minusSeconds(240), start + 12);
        send(vehicle, now.minusSeconds(180), start + 24);
        send(vehicle, now.minusSeconds(120), start + 30);
        send(vehicle, now.minusSeconds(60), start + 47);

        waitUntil(() -> mileage(vehicle.id()) == start + 47);
        assertThat(jdbc.sql("""
                select mileage from mileage_reading where vehicle_id = :id and source = 'TELEMETRY' and mileage > :start
                order by read_at""").param("id", vehicle.id()).param("start", start).query(Integer.class).list())
                .containsExactly(start + 24, start + 47);
    }

    @Test
    void an_unknown_vehicle_or_an_unreadable_message_does_not_stop_the_flow() throws Exception {
        kafka.send(TelemetryMessages.TOPIC_MILEAGE, "garbage", "not json").get();
        send(new Current(UUID.randomUUID(), UUID.randomUUID(), "ZZ-999-ZZ", 0), Instant.now(), 50_000);
        Current vehicle = jdbc.sql("""
                select id, company_id, registration, mileage from vehicle where status = 'ACTIVE'
                order by registration desc limit 1""").query(Current.class).single();
        send(vehicle, Instant.now(), vehicle.mileage() + 120);
        waitUntil(() -> mileage(vehicle.id()) == vehicle.mileage() + 120);
    }

    private void send(Current vehicle, Instant at, int km) throws Exception {
        kafka.send(TelemetryMessages.TOPIC_MILEAGE, vehicle.id().toString(), json.writeValueAsString(
                new MileageReport(vehicle.id(), vehicle.companyId(), vehicle.registration(), at, km))).get();
    }

    private int mileage(UUID id) {
        return jdbc.sql("select mileage from vehicle where id = :id").param("id", id).query(Integer.class).single();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition reached in time").isLessThan(deadline);
            Thread.sleep(200);
        }
    }
}
