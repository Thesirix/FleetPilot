package com.fleetpilot.telemetry.infrastructure.kafka;

import java.util.ArrayList;
import java.util.List;

import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.telemetry.application.TelemetryIngestion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the telemetry topics in batches (a few hundred messages at a time): the positions arrive at about fifty a
 * second, a batch is stored in one transaction. One consumer per partition, so the messages of a vehicle stay in order.
 * An unreadable message is logged and skipped: a box sending garbage must not stop the others.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.telemetry.consumers.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryListeners {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryListeners.class);

    private final TelemetryIngestion ingestion;
    private final JsonMapper json;

    public TelemetryListeners(TelemetryIngestion ingestion, JsonMapper json) {
        this.ingestion = ingestion;
        this.json = json;
    }

    @KafkaListener(topics = TelemetryMessages.TOPIC_POSITION, groupId = "telemetry-service", batch = "true",
            concurrency = "3")
    public void positions(List<String> messages) {
        List<PositionReport> reports = parse(messages, PositionReport.class);
        if (!reports.isEmpty()) {
            ingestion.positions(reports);
        }
    }

    @KafkaListener(topics = TelemetryMessages.TOPIC_MILEAGE, groupId = "telemetry-service", batch = "true")
    public void mileages(List<String> messages) {
        List<MileageReport> reports = parse(messages, MileageReport.class);
        if (!reports.isEmpty()) {
            ingestion.mileages(reports);
        }
    }

    @KafkaListener(topics = TelemetryMessages.TOPIC_FAULT, groupId = "telemetry-service", batch = "true")
    public void faults(List<String> messages) {
        parse(messages, FaultReport.class).forEach(ingestion::fault);
    }

    private <T> List<T> parse(List<String> messages, Class<T> type) {
        List<T> parsed = new ArrayList<>(messages.size());
        for (String message : messages) {
            try {
                parsed.add(json.readValue(message, type));
            } catch (JacksonException e) {
                LOG.warn("Unreadable {} skipped: {}", type.getSimpleName(), e.getOriginalMessage());
            }
        }
        return parsed;
    }
}
