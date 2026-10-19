package com.fleetpilot.bigdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import com.fleetpilot.bigdata.backfill.UsageHistoryBackfill;
import com.fleetpilot.bigdata.reference.FleetReference;
import com.fleetpilot.bigdata.sink.ElasticsearchSink;
import com.fleetpilot.bigdata.streams.JsonSerde;
import com.fleetpilot.bigdata.streams.Model;
import com.fleetpilot.bigdata.streams.Model.VehicleRef;
import com.fleetpilot.bigdata.streams.Topics;
import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The pipeline on a real Kafka broker and a real Elasticsearch: messages go through the topology, the outputs are
 * indexed with the templates' mappings, and the history of the telemetry service is loaded once.
 */
@SpringBootTest(properties = {
    "spring.kafka.streams.properties.commit.interval.ms=500",
    "spring.kafka.streams.application-id=analytics-it",
    "fleetpilot.services.vehicle-url=http://localhost:1",
    "spring.kafka.listener.idle-between-polls=100"
})
@Import(PipelineIT.Stubs.class)
@DisplayName("Analytics pipeline on Kafka and Elasticsearch")
class PipelineIT {

    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.2");

    @ServiceConnection
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
            DockerImageName.parse("elasticsearch:9.5.2")
                    .asCompatibleSubstituteFor("docker.elastic.co/elasticsearch/elasticsearch"))
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");

    static {
        KAFKA.start();
        ELASTICSEARCH.start();
    }

    /** A fresh state directory: the local state of an earlier run belongs to another broker. */
    @DynamicPropertySource
    static void streams(DynamicPropertyRegistry registry) throws IOException {
        Path state = Files.createTempDirectory("analytics-it");
        registry.add("spring.kafka.streams.state-dir", state::toString);
    }

    static final UUID COMPANY = UUID.randomUUID();
    static final UUID CAR = UUID.randomUUID();
    static final UUID VAN = UUID.randomUUID();

    @Autowired
    private KafkaTemplate<String, String> kafka;
    @Autowired
    private ElasticsearchClient elasticsearch;

    @TestConfiguration
    static class Stubs {

        /** Two vehicles, published on the reference topic as the real loader would. */
        @Bean
        @Primary
        FleetReference stubFleet(KafkaTemplate<String, String> kafka) {
            return new FleetReference(RestClient.create(), kafka) {
                @Override
                protected List<VehicleRef> load() {
                    return List.of(
                            new VehicleRef(CAR, COMPANY, "Néolia Énergies", "GH-482-KM", "CAR", "DIESEL", "Peugeot",
                                    "308 SW"),
                            new VehicleRef(VAN, COMPANY, "Néolia Énergies", "FT-118-BC", "VAN", "ELECTRIC", "Renault",
                                    "Kangoo E-Tech"));
                }
            };
        }

        /** The telemetry service answers one page of history. */
        @Bean
        @Primary
        UsageHistoryBackfill stubBackfill(FleetReference fleet, ElasticsearchClient elasticsearch,
                ElasticsearchSink sink, Clock clock) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://telemetry");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            LocalDate yesterday = LocalDate.now(clock).minusDays(1);
            server.expect(ExpectedCount.manyTimes(), requestTo(org.hamcrest.Matchers.startsWith(
                    "http://telemetry/api/v1/telemetry/usage-days"))).andRespond(withSuccess("""
                    {"content":[
                     {"vehicleId":"%s","companyId":"%s","day":"%s","distanceKm":64.2,"trips":4,"longestTripKm":31.0,
                      "drivingMinutes":88,"maxSpeed":121},
                     {"vehicleId":"%s","companyId":"%s","day":"%s","distanceKm":102.0,"trips":9,"longestTripKm":22.5,
                      "drivingMinutes":171,"maxSpeed":98}],
                     "page":0,"size":5000,"totalElements":2,"totalPages":1}"""
                    .formatted(CAR, COMPANY, yesterday, VAN, COMPANY, yesterday), MediaType.APPLICATION_JSON));
            return new UsageHistoryBackfill(builder.build(), fleet, elasticsearch, sink, clock);
        }
    }

    private long count(String index, String query) throws IOException {
        if (!elasticsearch.indices().exists(b -> b.index(index)).value()) {
            return 0;
        }
        elasticsearch.indices().refresh(b -> b.index(index));
        return elasticsearch.count(b -> b.index(index).q(query)).count();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 90_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("condition reached in time").isLessThan(deadline);
            Thread.sleep(500);
        }
    }

    /** Waits for documents; on timeout, shows what the index holds, to understand why. */
    private void awaitDocuments(String index, String query, long expected) throws Exception {
        try {
            waitUntil(documents(index, query, expected));
        } catch (AssertionError e) {
            boolean exists = elasticsearch.indices().exists(b -> b.index(index)).value();
            throw new AssertionError("%s has no %d documents matching %s; it holds %s; topics: %s".formatted(index,
                    expected, query, exists ? elasticsearch.search(b -> b.index(index).size(3), Object.class).hits()
                            .hits().stream().map(h -> String.valueOf(h.source())).toList() : "nothing",
                    topicCounts()), e);
        }
    }

    /** Records in every topic of the broker, to see where a flow stops. */
    private static String topicCounts() {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "diag-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(config)) {
            Map<String, Long> counts = new TreeMap<>();
            consumer.listTopics().forEach((topic, partitions) -> {
                var tps = partitions.stream().map(p -> new TopicPartition(topic, p.partition()))
                        .toList();
                counts.put(topic, consumer.endOffsets(tps).values().stream().mapToLong(Long::longValue).sum());
            });
            return counts.toString();
        }
    }

    private BooleanSupplier documents(String index, String query, long expected) {
        return () -> {
            try {
                return count(index, query) >= expected;
            } catch (IOException e) {
                return false;
            }
        };
    }

    @Test
    void the_flows_are_aggregated_and_indexed_with_their_mappings_and_the_history_is_loaded() throws Exception {
        waitUntil(documents("fleet-vehicle-day", "source:history", 2));

        Instant start = Instant.now().minusSeconds(600);
        for (int s = 0; s <= 240; s += 2) {
            Instant at = start.plusSeconds(s);
            kafka.send(TelemetryMessages.TOPIC_POSITION, CAR.toString(), JsonSerde.write(new PositionReport(CAR,
                    COMPANY, "GH-482-KM", at, 43.30 + s / 10_000.0, 5.37, 60, 90, true, null)));
        }
        kafka.send(TelemetryMessages.TOPIC_MILEAGE, CAR.toString(), JsonSerde.write(new MileageReport(CAR, COMPANY,
                "GH-482-KM", start, 82_000)));
        kafka.send(TelemetryMessages.TOPIC_MILEAGE, CAR.toString(), JsonSerde.write(new MileageReport(CAR, COMPANY,
                "GH-482-KM", start.plusSeconds(240), 82_004)));
        for (String[] fill : new String[][] {{"CBP-1", "82000"}, {"CBP-2", "82650"}}) {
            kafka.send(Topics.FUEL, "GH-482-KM", JsonSerde.write(new Model.FuelTransaction(UUID.randomUUID(),
                    "Carbu Pro", fill[0], start, "Relais Prado", "Marseille", "GAZOLE", new BigDecimal("39.00"),
                    new BigDecimal("1.72"), new BigDecimal("67.08"), Integer.valueOf(fill[1]), "GH-482-KM", CAR,
                    COMPANY, "DIESEL", null)));
        }
        // a later position moves the time of the stream on: the aggregates are emitted
        kafka.send(TelemetryMessages.TOPIC_POSITION, CAR.toString(), JsonSerde.write(new PositionReport(CAR, COMPANY,
                "GH-482-KM", start.plusSeconds(400), 43.33, 5.37, 0, 90, false, null)));
        kafka.flush();

        awaitDocuments("fleet-positions", "registration:GH-482-KM AND companyName:\"Néolia Énergies\"", 4);
        awaitDocuments("fleet-fuel", "consumptionL100:6", 1);
        awaitDocuments("fleet-fuel", "*", 2);
        awaitDocuments("fleet-activity", "movingVehicles:1", 1);
        awaitDocuments("fleet-vehicle-day", "source:live AND distanceKm:4", 1);

        Property location = elasticsearch.indices().getMapping(b -> b.index("fleet-positions"))
                .get("fleet-positions").mappings().properties().get("location");
        assertThat(location.isGeoPoint()).isTrue();
    }
}
