package com.fleetpilot.messaging;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox and idempotent consumption for every service that has a database and Kafka. The relay runs every second
 * ({@code fleetpilot.outbox.relay.interval}) and can be switched off with {@code fleetpilot.outbox.relay.enabled}.
 */
@AutoConfiguration(afterName = {
    "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
    "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
    "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
})
@ConditionalOnBean(JdbcTemplate.class)
@EnableScheduling
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(JdbcTemplate jdbc, JsonMapper jsonMapper, Clock clock,
            @Value("${spring.application.name}") String source) {
        return new OutboxPublisher(jdbc, jsonMapper, clock, source);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProcessedEvents processedEvents(JdbcTemplate jdbc) {
        return new ProcessedEvents(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(name = "fleetpilot.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    public OutboxRelay outboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, Clock clock) {
        return new OutboxRelay(jdbc, kafka, clock);
    }
}
