package com.fleetpilot.bigdata.api;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fleetpilot.bigdata.streams.Topics;
import com.fleetpilot.common.security.Roles;
import org.apache.kafka.streams.KafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** State of the pipeline: the Kafka Streams application and the documents of each analytics index. */
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize(Roles.ANY_STAFF)
public class AnalyticsController {

    private final StreamsBuilderFactoryBean streams;
    private final ElasticsearchClient elasticsearch;

    public AnalyticsController(StreamsBuilderFactoryBean streams, ElasticsearchClient elasticsearch) {
        this.streams = streams;
        this.elasticsearch = elasticsearch;
    }

    public record Status(String streams, Map<String, Long> documents) {
    }

    @GetMapping("/status")
    public Status status() throws IOException {
        KafkaStreams kafkaStreams = streams.getKafkaStreams();
        Map<String, Long> documents = new LinkedHashMap<>();
        for (String index : Topics.INDICES.values().stream().sorted().toList()) {
            boolean exists = elasticsearch.indices().exists(b -> b.index(index)).value();
            documents.put(index, exists ? elasticsearch.count(b -> b.index(index)).count() : 0L);
        }
        return new Status(kafkaStreams == null ? "NOT_STARTED" : kafkaStreams.state().name(), documents);
    }
}
