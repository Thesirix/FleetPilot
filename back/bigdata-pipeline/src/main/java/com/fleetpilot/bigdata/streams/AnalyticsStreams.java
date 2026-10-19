package com.fleetpilot.bigdata.streams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fleetpilot.common.telemetry.TelemetryMessages;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.streams.StreamsBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Builds the topology on the builder Spring manages: Kafka Streams starts and stops with the application. The topics
 * it reads and writes are created first (a global table cannot start on a topic that does not exist yet); creating a
 * topic that exists is harmless.
 */
@Component
public class AnalyticsStreams {

    public AnalyticsStreams(StreamsBuilder builder, KafkaAdmin admin,
            @Value("${fleetpilot.analytics.report-seconds}") long reportSeconds) {
        admin.createOrModifyTopics(topics().toArray(NewTopic[]::new));
        AnalyticsTopology.build(builder, reportSeconds);
    }

    List<NewTopic> topics() {
        List<NewTopic> topics = new ArrayList<>();
        topics.add(TopicBuilder.name(Topics.VEHICLES).partitions(3).replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT).build());
        // inputs: the same partitioning as the producers (simulator, telemetry service, integration bus)
        for (String input : List.of(TelemetryMessages.TOPIC_POSITION, TelemetryMessages.TOPIC_MILEAGE,
                TelemetryMessages.TOPIC_FAULT)) {
            topics.add(TopicBuilder.name(input).partitions(6).replicas(1).build());
        }
        topics.add(TopicBuilder.name(Topics.ALERTS).partitions(3).replicas(1).build());
        topics.add(TopicBuilder.name(Topics.FUEL).partitions(3).replicas(1).build());
        String retention = String.valueOf(Duration.ofDays(3).toMillis());
        for (String output : Topics.INDICES.keySet()) {
            topics.add(TopicBuilder.name(output).partitions(3).replicas(1)
                    .config(TopicConfig.RETENTION_MS_CONFIG, retention).build());
        }
        return topics;
    }
}
