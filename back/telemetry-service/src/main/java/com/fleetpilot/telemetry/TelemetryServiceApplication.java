package com.fleetpilot.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Connected vehicles of FleetPilot: telemetry from Kafka, live state, trips, usage and alerts. */
@SpringBootApplication
public class TelemetryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryServiceApplication.class, args);
    }
}
