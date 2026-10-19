package com.fleetpilot.vehicle.infrastructure.messaging;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.messaging.IncomingEvent;
import com.fleetpilot.vehicle.domain.model.VehicleAlert;
import com.fleetpilot.vehicle.domain.repository.VehicleAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps a copy of the alerts raised by the connected vehicles, so the driver of a vehicle sees them in the app. The
 * alert id is the key: an event delivered twice is stored once.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.vehicle-alerts.enabled", havingValue = "true", matchIfMissing = true)
public class VehicleAlertListener {

    private static final Logger LOG = LoggerFactory.getLogger(VehicleAlertListener.class);

    private final VehicleAlertRepository alerts;
    private final JsonMapper json;

    public VehicleAlertListener(VehicleAlertRepository alerts, JsonMapper json) {
        this.alerts = alerts;
        this.json = json;
    }

    @KafkaListener(topics = EventTypes.TOPIC_ALERTS, groupId = "vehicle-service.alerts",
            properties = "auto.offset.reset=earliest")
    @Transactional
    public void onEvent(String message) {
        IncomingEvent event;
        try {
            event = IncomingEvent.parse(json, message);
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            LOG.warn("Unreadable alert event skipped: {}", e.getMessage());
            return;
        }
        if (!EventTypes.ALERT_RAISED.equals(event.type())) {
            return;
        }
        UUID alertId = event.uuid("alertId").orElse(null);
        UUID vehicleId = event.uuid("vehicleId").orElse(null);
        String type = event.text("type");
        Instant raisedAt = instant(event.text("raisedAt"));
        if (alertId == null || vehicleId == null || type == null || raisedAt == null) {
            LOG.warn("Incomplete alert event {} skipped", event.eventId());
            return;
        }
        if (alerts.existsById(alertId)) {
            return;
        }
        String severity = event.text("severity");
        String text = event.text("message");
        alerts.save(new VehicleAlert(alertId, vehicleId, type, severity == null ? "INFO" : severity,
                text == null ? type : text, raisedAt));
    }

    private static Instant instant(String value) {
        try {
            return value == null ? null : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
