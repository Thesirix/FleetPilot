package com.fleetpilot.bigdata.streams;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import com.fleetpilot.bigdata.streams.Model.ActivityDoc;
import com.fleetpilot.bigdata.streams.Model.AlertDoc;
import com.fleetpilot.bigdata.streams.Model.EnrichedPosition;
import com.fleetpilot.bigdata.streams.Model.FaultDoc;
import com.fleetpilot.bigdata.streams.Model.FuelDoc;
import com.fleetpilot.bigdata.streams.Model.VehicleDayDoc;
import com.fleetpilot.bigdata.streams.Model.VehicleRef;
import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Analytics topology")
class AnalyticsTopologyTest {

    static final UUID COMPANY = UUID.randomUUID();
    static final UUID CAR = UUID.randomUUID();
    static final UUID VAN = UUID.randomUUID();
    static final UUID TRUCK = UUID.randomUUID();
    /** Tuesday 14 October 2026, 08:00 in Paris. */
    static final Instant T0 = Instant.parse("2026-10-14T06:00:00Z");

    private TopologyTestDriver driver;
    private TestInputTopic<String, String> positions;
    private TestInputTopic<String, String> mileage;

    @BeforeEach
    void start() throws Exception {
        StreamsBuilder builder = new StreamsBuilder();
        AnalyticsTopology.build(builder, 60);
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "analytics-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        config.put(StreamsConfig.STATE_DIR_CONFIG, Files.createTempDirectory("streams").toString());
        driver = new TopologyTestDriver(builder.build(), config);
        TestInputTopic<String, String> vehicles = input(Topics.VEHICLES);
        vehicles.pipeInput(CAR.toString(), JsonSerde.write(new VehicleRef(CAR, COMPANY, "Néolia Énergies", "GH-482-KM",
                "CAR", "DIESEL", "Peugeot", "308 SW")));
        vehicles.pipeInput(VAN.toString(), JsonSerde.write(new VehicleRef(VAN, COMPANY, "Néolia Énergies", "FT-118-BC",
                "VAN", "DIESEL", "Renault", "Trafic")));
        vehicles.pipeInput(TRUCK.toString(), JsonSerde.write(new VehicleRef(TRUCK, COMPANY, "Néolia Énergies",
                "EA-301-PL", "TRUCK", "DIESEL", "Renault Trucks", "D Wide")));
        positions = input(TelemetryMessages.TOPIC_POSITION);
        mileage = input(TelemetryMessages.TOPIC_MILEAGE);
    }

    @AfterEach
    void stop() {
        driver.close();
    }

    private TestInputTopic<String, String> input(String topic) {
        return driver.createInputTopic(topic, new StringSerializer(), new StringSerializer());
    }

    private <T> List<KeyValue<String, T>> output(String topic, Class<T> type) {
        TestOutputTopic<String, String> out = driver.createOutputTopic(topic, new StringDeserializer(),
                new StringDeserializer());
        return out.readKeyValuesToList().stream().map(kv -> KeyValue.pair(kv.key, JsonSerde.read(kv.value, type)))
                .toList();
    }

    private void position(UUID vehicle, Instant at, int speed, boolean ignition) {
        positions.pipeInput(vehicle.toString(), JsonSerde.write(new PositionReport(vehicle, COMPANY, "X", at, 43.3,
                5.37, speed, 90, ignition, null)), at);
    }

    @Test
    void positions_are_kept_once_a_minute_per_vehicle_with_the_vehicle_and_its_company() {
        for (int s = 0; s <= 140; s += 2) {
            position(CAR, T0.plusSeconds(s), 50, true);
        }
        List<KeyValue<String, EnrichedPosition>> kept = output(Topics.POSITIONS_OUT, EnrichedPosition.class);

        assertThat(kept).extracting(kv -> kv.value.at()).containsExactly(T0, T0.plusSeconds(60), T0.plusSeconds(120));
        assertThat(kept.getFirst().key).isEqualTo(CAR + "|" + T0.toEpochMilli());
        assertThat(kept.getFirst().value.companyName()).isEqualTo("Néolia Énergies");
        assertThat(kept.getFirst().value.category()).isEqualTo("CAR");
        assertThat(kept.getFirst().value.location().lat()).isEqualTo(43.3);
    }

    @Test
    void the_fleet_activity_counts_the_vehicles_moving_and_idling_per_window_of_five_minutes() {
        position(CAR, T0.plusSeconds(10), 50, true);
        position(VAN, T0.plusSeconds(20), 70, true);
        position(TRUCK, T0.plusSeconds(30), 0, true);
        position(CAR, T0.plusSeconds(40), 30, true);
        // stream time moves on: the window is emitted
        position(CAR, T0.plusSeconds(400), 0, false);

        List<KeyValue<String, ActivityDoc>> windows = output(Topics.ACTIVITY_OUT, ActivityDoc.class);
        // the last state of the window is the one Elasticsearch keeps
        ActivityDoc first = windows.stream().filter(kv -> kv.value.windowStart().equals(T0)).reduce((a, b) -> b)
                .orElseThrow().value;
        assertThat(first.connectedVehicles()).isEqualTo(3);
        assertThat(first.movingVehicles()).isEqualTo(2);
        assertThat(first.idlingVehicles()).isOne();
        assertThat(first.averageSpeed()).isEqualTo(50);
        assertThat(first.companyName()).isEqualTo("Néolia Énergies");
    }

    @Test
    void the_distance_of_a_day_comes_from_the_odometer_and_the_driving_time_from_the_positions() {
        mileage.pipeInput(VAN.toString(), JsonSerde.write(new MileageReport(VAN, COMPANY, "FT-118-BC", T0, 61_300)), T0);
        for (int minute = 1; minute <= 45; minute++) {
            position(VAN, T0.plusSeconds(minute * 60L), minute == 20 ? 112 : 65, true);
        }
        Instant later = T0.plusSeconds(3_600);
        mileage.pipeInput(VAN.toString(), JsonSerde.write(new MileageReport(VAN, COMPANY, "FT-118-BC", later, 61_347)),
                later);
        // stream time moves on: the day, updated less than 30 s ago, is emitted
        position(CAR, later.plusSeconds(60), 0, false);

        List<KeyValue<String, VehicleDayDoc>> days = output(Topics.VEHICLE_DAY_OUT, VehicleDayDoc.class);
        KeyValue<String, VehicleDayDoc> last = days.stream().filter(kv -> kv.key.startsWith(VAN.toString()))
                .reduce((a, b) -> b).orElseThrow();
        VehicleDayDoc day = last.value;
        assertThat(last.key).isEqualTo(VAN + "|2026-10-14");
        assertThat(day.day()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(day.distanceKm()).isEqualTo(47);
        assertThat(day.drivingMinutes()).isEqualTo(45);
        assertThat(day.maxSpeed()).isEqualTo(112);
        assertThat(day.registration()).isEqualTo("FT-118-BC");
        assertThat(day.source()).isEqualTo("live");
    }

    @Test
    void the_consumption_is_computed_from_one_fill_to_the_next_of_the_same_vehicle() {
        TestInputTopic<String, String> fuel = input(Topics.FUEL);
        fuel.pipeInput("GH-482-KM", fill("CBP-1", "GAZOLE", "45.00", 82_000, null), T0);
        fuel.pipeInput("GH-482-KM", fill("CBP-2", "ADBLUE", "10.00", 82_150, null), T0.plusSeconds(3_600));
        fuel.pipeInput("GH-482-KM", fill("CBP-3", "GAZOLE", "42.00", 82_600, "TOO_FREQUENT,ODOMETER"),
                T0.plusSeconds(86_400));

        List<KeyValue<String, FuelDoc>> docs = output(Topics.FUEL_OUT, FuelDoc.class);
        assertThat(docs).hasSize(3);
        assertThat(docs.getFirst().value.consumptionL100()).isNull();
        assertThat(docs.get(1).value.consumptionL100()).isNull();
        FuelDoc third = docs.get(2).value;
        assertThat(third.distanceSincePreviousKm()).isEqualTo(600);
        assertThat(third.consumptionL100()).isEqualByComparingTo("7.0");
        assertThat(third.anomalies()).containsExactly("TOO_FREQUENT", "ODOMETER");
        assertThat(third.companyName()).isEqualTo("Néolia Énergies");
    }

    private static String fill(String reference, String product, String litres, int odometer, String anomalies) {
        return JsonSerde.write(new Model.FuelTransaction(UUID.nameUUIDFromBytes(reference.getBytes()), "Carbu Pro",
                reference, T0, "Relais Prado", "Marseille", product, new BigDecimal(litres), new BigDecimal("1.72"),
                new BigDecimal(litres).multiply(new BigDecimal("1.72")), odometer, "GH-482-KM", CAR, COMPANY,
                "DIESEL", anomalies));
    }

    @Test
    void faults_and_alerts_are_enriched_and_unreadable_messages_are_ignored() {
        input(TelemetryMessages.TOPIC_FAULT).pipeInput(TRUCK.toString(), JsonSerde.write(new FaultReport(TRUCK, COMPANY,
                "EA-301-PL", T0, "P0217", "Surchauffe du moteur", TelemetryMessages.Severity.HIGH)), T0);
        input(TelemetryMessages.TOPIC_FAULT).pipeInput(TRUCK.toString(), "not json", T0);
        UUID alert = UUID.randomUUID();
        input(Topics.ALERTS).pipeInput(CAR.toString(), """
                {"eventId":"%s","type":"alert.raised","occurredAt":"%s","companyId":"%s","payload":{"alertId":"%s",
                 "vehicleId":"%s","registration":"GH-482-KM","type":"SPEEDING","severity":"WARNING",
                 "message":"152 km/h","latitude":43.3,"longitude":5.37,"raisedAt":"%s"}}"""
                .formatted(UUID.randomUUID(), T0, COMPANY, alert, CAR, T0), T0);

        List<KeyValue<String, FaultDoc>> faults = output(Topics.FAULTS_OUT, FaultDoc.class);
        assertThat(faults).singleElement().satisfies(kv -> {
            assertThat(kv.value.makeModel()).isEqualTo("Renault Trucks D Wide");
            assertThat(kv.value.severity()).isEqualTo("HIGH");
        });
        List<KeyValue<String, AlertDoc>> alerts = output(Topics.ALERTS_OUT, AlertDoc.class);
        assertThat(alerts).singleElement().satisfies(kv -> {
            assertThat(kv.key).isEqualTo(alert.toString());
            assertThat(kv.value.category()).isEqualTo("CAR");
            assertThat(kv.value.location().lon()).isEqualTo(5.37);
        });
    }
}
