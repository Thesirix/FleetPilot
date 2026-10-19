package com.fleetpilot.bigdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The analytics pipeline of FleetPilot: Kafka Streams aggregations indexed into Elasticsearch for Kibana. */
@SpringBootApplication
public class BigDataApplication {

    public static void main(String[] args) {
        SpringApplication.run(BigDataApplication.class, args);
    }
}
