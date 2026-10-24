package com.fleetpilot.integration.route.fuelcard;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Creates the topic {@code fuel.transactions} before the fuel card route publishes on it. The broker does not create
 * topics on the fly, and the analytics pipeline, which also declares it, only runs with the observability profile:
 * without this, a fresh platform would block every fuel card file on "topic not present in metadata".
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.integration.fuel-events-enabled", havingValue = "true")
public class FuelTopic implements InitializingBean {

    static final String NAME = "fuel.transactions";
    private static final Logger LOG = LoggerFactory.getLogger(FuelTopic.class);

    private final String brokers;

    public FuelTopic(@Value("${camel.component.kafka.brokers}") String brokers) {
        this.brokers = brokers;
    }

    @Override
    public void afterPropertiesSet() throws InterruptedException {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 30_000))) {
            // same layout as declared by the analytics pipeline
            admin.createTopics(List.of(new NewTopic(NAME, 3, (short) 1))).all().get(30, TimeUnit.SECONDS);
            LOG.info("Topic {} created", NAME);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                return;
            }
            LOG.warn("Topic {} not created: {}", NAME, e.getCause().getMessage());
        } catch (TimeoutException e) {
            LOG.warn("Topic {} not created: Kafka did not answer", NAME);
        }
    }
}
