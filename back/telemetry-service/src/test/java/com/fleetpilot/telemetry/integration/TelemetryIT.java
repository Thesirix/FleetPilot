package com.fleetpilot.telemetry.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.telemetry.application.FleetReference;
import com.fleetpilot.telemetry.domain.LiveVehicle.VehicleInfo;
import com.jayway.jsonpath.JsonPath;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * The telemetry service on a real PostgreSQL and a real Kafka broker: the messages of the boxes go through the
 * listeners, the rules, the database, the outbox and the REST API.
 */
@SpringBootTest(properties = {
    "fleetpilot.demo-data.enabled=false",
    "fleetpilot.outbox.relay.enabled=true",
    "fleetpilot.outbox.relay.interval=500",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@AutoConfigureMockMvc
@Import(TelemetryIT.TestBeans.class)
@DisplayName("Telemetry service on Kafka and PostgreSQL")
class TelemetryIT {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    private static final UUID NEOLIA = UUID.nameUUIDFromBytes("company-neolia".getBytes());
    private static final UUID METROPOLE = UUID.nameUUIDFromBytes("company-metropole".getBytes());
    /** Vehicles whose next service is due at 60,000 km. */
    private static final int SERVICE_DUE = 60_000;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private JsonMapper json;
    @Autowired
    private JdbcClient jdbc;

    @TestConfiguration
    static class TestBeans {

        /** Every vehicle is a diesel car, with a service due at 60,000 km. */
        @Bean
        @Primary
        FleetReference stubFleetReference() {
            return vehicleId -> new VehicleInfo("CAR", "DIESEL", SERVICE_DUE);
        }
    }

    private static RequestPostProcessor admin() {
        return user("theo.lambert", Roles.PLATFORM_ADMIN, Map.of());
    }

    private static RequestPostProcessor clientManager(UUID company) {
        return user("m.roux@neolia-energies.fr", Roles.CLIENT_MANAGER,
                Map.of(CurrentUser.COMPANY_CLAIM, company.toString()));
    }

    private static RequestPostProcessor user(String username, String role, Map<String, Object> claims) {
        return jwt().jwt(token -> {
            token.claim("preferred_username", username).claim("realm_access", Map.of("roles", List.of(role)));
            claims.forEach(token::claim);
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void a_trip_at_excessive_speed_is_recorded_alerted_published_and_handled() throws Exception {
        UUID vehicle = UUID.randomUUID();
        Instant start = Instant.now().minusSeconds(120);
        List<PositionReport> positions = new ArrayList<>();
        positions.add(position(vehicle, NEOLIA, start, 5.370, 0, true));
        int[] speeds = {120, 152, 155, 158, 149, 125};
        for (int i = 0; i < speeds.length; i++) {
            positions.add(position(vehicle, NEOLIA, start.plusSeconds(10L * (i + 1)), 5.370 + 0.004 * (i + 1),
                    speeds[i], true));
        }
        positions.add(position(vehicle, NEOLIA, start.plusSeconds(80), 5.400, 0, false));
        for (PositionReport position : positions) {
            send(TelemetryMessages.TOPIC_POSITION, vehicle, position);
        }

        waitUntil(() -> count("select count(*) from trip where vehicle_id = '" + vehicle + "'") == 1);
        waitUntil(() -> count("select count(*) from alert where vehicle_id = '" + vehicle + "'") == 1);

        String alertId = JsonPath.read(mvc.perform(get("/api/v1/alerts").param("vehicleId", vehicle.toString())
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("SPEEDING"))
                .andExpect(jsonPath("$.content[0].message").value("158 km/h pour une limite de 130 km/h"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andReturn().getResponse().getContentAsString(), "$.content[0].id");

        mvc.perform(get("/api/v1/telemetry/vehicles/{id}", vehicle).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.activity").value("PARKED"))
                .andExpect(jsonPath("$.state.registration").value("AB-123-CD"))
                .andExpect(jsonPath("$.trips.length()").value(1))
                .andExpect(jsonPath("$.trips[0].maxSpeed").value(158))
                .andExpect(jsonPath("$.usage[0].trips").value(1));

        List<ConsumerRecord<String, String>> published = consume(EventTypes.TOPIC_ALERTS, vehicle);
        assertThat(published).singleElement().satisfies(r -> {
            assertThat(JsonPath.<String>read(r.value(), "$.type")).isEqualTo(EventTypes.ALERT_RAISED);
            assertThat(JsonPath.<String>read(r.value(), "$.payload.type")).isEqualTo("SPEEDING");
        });

        mvc.perform(post("/api/v1/alerts/{id}/acknowledge", alertId).with(admin()))
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.handledBy").value("theo.lambert"));
        mvc.perform(post("/api/v1/alerts/{id}/resolve", alertId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"Rappel des règles fait au conducteur\"}"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.comment").value("Rappel des règles fait au conducteur"));
    }

    @Test
    void a_client_manager_only_sees_the_vehicles_of_their_company() throws Exception {
        UUID neolia = UUID.randomUUID();
        UUID metropole = UUID.randomUUID();
        send(TelemetryMessages.TOPIC_POSITION, neolia, position(neolia, NEOLIA, Instant.now(), 5.37, 42, true));
        send(TelemetryMessages.TOPIC_POSITION, metropole, position(metropole, METROPOLE, Instant.now(), 4.83, 0, false));
        waitUntil(() -> count("select count(*) from vehicle_state where vehicle_id in ('" + neolia + "', '"
                + metropole + "')") == 2);

        mvc.perform(get("/api/v1/telemetry/live").with(clientManager(METROPOLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].vehicleId", hasItem(metropole.toString())))
                .andExpect(jsonPath("$[*].vehicleId", not(hasItem(neolia.toString()))));
        mvc.perform(get("/api/v1/telemetry/vehicles/{id}", neolia).with(clientManager(METROPOLE)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/telemetry/vehicles/{id}/track", neolia).with(clientManager(METROPOLE)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/telemetry/vehicles/{id}", neolia).with(clientManager(NEOLIA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.activity").value("MOVING"));
        mvc.perform(get("/api/v1/telemetry/vehicles/{id}", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void a_serious_trouble_code_raises_a_critical_alert_and_a_minor_one_is_only_recorded() throws Exception {
        UUID vehicle = UUID.randomUUID();
        send(TelemetryMessages.TOPIC_FAULT, vehicle, new FaultReport(vehicle, NEOLIA, "FK-901-LM", Instant.now(),
                "P0217", "Surchauffe moteur", TelemetryMessages.Severity.HIGH));
        send(TelemetryMessages.TOPIC_FAULT, vehicle, new FaultReport(vehicle, NEOLIA, "FK-901-LM", Instant.now(),
                "P0456", "Petite fuite du circuit EVAP", TelemetryMessages.Severity.LOW));
        waitUntil(() -> count("select count(*) from fault where vehicle_id = '" + vehicle + "'") == 2);

        mvc.perform(get("/api/v1/alerts").param("vehicleId", vehicle.toString()).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("ENGINE_FAULT"))
                .andExpect(jsonPath("$.content[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.content[0].message").value("P0217 : Surchauffe moteur"));
    }

    @Test
    void the_odometer_announces_the_next_service_once() throws Exception {
        UUID vehicle = UUID.randomUUID();
        send(TelemetryMessages.TOPIC_POSITION, vehicle, position(vehicle, NEOLIA, Instant.now(), 5.37, 30, true));
        waitUntil(() -> count("select count(*) from vehicle_state where vehicle_id = '" + vehicle + "'") == 1);
        for (int km : new int[] {59_200, 59_640, 59_700}) {
            send(TelemetryMessages.TOPIC_MILEAGE, vehicle, new MileageReport(vehicle, NEOLIA, "AB-123-CD",
                    Instant.now(), km));
        }
        waitUntil(() -> count("select count(*) from vehicle_state where odometer_km = 59700 and vehicle_id = '"
                + vehicle + "'") == 1);
        waitUntil(() -> count("select count(*) from alert where type = 'SERVICE_DUE' and vehicle_id = '"
                + vehicle + "'") == 1);
        assertThat(jdbc.sql("select occurrences from alert where vehicle_id = :id").param("id", vehicle)
                .query(Integer.class).single()).isPositive();
    }

    @Test
    void the_usage_profile_summarises_the_days_driven() throws Exception {
        UUID vehicle = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        LocalDate today = LocalDate.now();
        for (int day = 1; day <= 20; day++) {
            jdbc.sql("""
                    insert into daily_usage (vehicle_id, company_id, day, distance_km, trips, longest_trip_km,
                        driving_minutes, max_speed) values (:v, :c, :day, :km, 3, :longest, 60, 110)""")
                    .param("v", vehicle).param("c", company).param("day", today.minusDays(day))
                    .param("km", day == 5 ? 320 : 40 + day).param("longest", day == 5 ? 280 : 25).update();
        }

        mvc.perform(get("/api/v1/telemetry/usage-profiles").param("companyId", company.toString()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].activeDays").value(20))
                .andExpect(jsonPath("$[0].daysOver150Km").value(1))
                .andExpect(jsonPath("$[0].longestTripKm").value(280.0))
                .andExpect(jsonPath("$[0].maxDailyKm").value(320.0));

        mvc.perform(get("/api/v1/telemetry/usage-days").param("from", today.minusDays(30).toString())
                        .param("size", "5000").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.vehicleId == '%s')]".formatted(vehicle)).isNotEmpty());
        mvc.perform(get("/api/v1/telemetry/usage-days").param("from", today.toString()).with(clientManager(NEOLIA)))
                .andExpect(status().isForbidden());
    }

    private static PositionReport position(UUID vehicle, UUID company, Instant at, double lon, int speed,
            boolean ignition) {
        return new PositionReport(vehicle, company, "AB-123-CD", at, 43.30, lon, speed, 90, ignition, null);
    }

    private void send(String topic, UUID vehicle, Object message) throws Exception {
        kafka.send(topic, vehicle.toString(), json.writeValueAsString(message)).get();
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

    /** The messages of a topic about one vehicle (keyed by its id). */
    private static List<ConsumerRecord<String, String>> consume(String topic, UUID key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "telemetry-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 20_000;
            while (records.isEmpty() && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                    if (key.toString().equals(r.key())) {
                        records.add(r);
                    }
                });
            }
        }
        return records;
    }
}
