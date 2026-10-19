package com.fleetpilot.simulator;

import java.time.Clock;

import com.fleetpilot.common.client.ServiceRestClients;
import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.simulator.engine.Simulation;
import com.fleetpilot.simulator.fleet.FleetClient;
import com.fleetpilot.simulator.fleet.KafkaTelemetrySink;
import com.fleetpilot.simulator.fleet.SimulatorRunner;
import com.fleetpilot.simulator.road.RoadNetwork;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plays the boxes of the connected vehicles: real roads around the sites of the client companies, positions, odometer
 * and trouble codes sent to Kafka, like a telematics provider would.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SimulatorApplication {

    /** Telemetry is kept three days on Kafka: the telemetry service stores what must last. */
    private static final String RETENTION_MS = String.valueOf(3L * 24 * 3600 * 1000);

    public static void main(String[] args) {
        SpringApplication.run(SimulatorApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RoadNetwork roadNetwork(JsonMapper json) {
        return RoadNetwork.load(json, "/simulator/routes.json");
    }

    @Bean
    Simulation simulation(RoadNetwork roads, KafkaTemplate<String, String> kafka, JsonMapper json, Clock clock) {
        return new Simulation(roads, new KafkaTelemetrySink(kafka, json), clock);
    }

    @Bean
    FleetClient fleetClient(RestClient.Builder builder, OAuth2AuthorizedClientManager manager,
            @Value("${fleetpilot.services.vehicle-url}") String baseUrl) {
        return new FleetClient(ServiceRestClients.create(builder, manager, "vehicle-simulator", baseUrl));
    }

    @Bean
    @ConditionalOnProperty(name = "fleetpilot.simulator.enabled", havingValue = "true", matchIfMissing = true)
    SimulatorRunner simulatorRunner(FleetClient fleet, RoadNetwork roads, Simulation simulation,
            @Value("${fleetpilot.simulator.vehicles:160}") int vehicles) {
        return new SimulatorRunner(fleet, roads, simulation, vehicles);
    }

    @Bean
    NewTopic positionTopic() {
        return TopicBuilder.name(TelemetryMessages.TOPIC_POSITION).partitions(6).replicas(1)
                .config("retention.ms", RETENTION_MS).build();
    }

    @Bean
    NewTopic mileageTopic() {
        return TopicBuilder.name(TelemetryMessages.TOPIC_MILEAGE).partitions(6).replicas(1)
                .config("retention.ms", RETENTION_MS).build();
    }

    @Bean
    NewTopic faultTopic() {
        return TopicBuilder.name(TelemetryMessages.TOPIC_FAULT).partitions(3).replicas(1).build();
    }
}
