package com.fleetpilot.vehicle.infrastructure.messaging;

import com.fleetpilot.common.telemetry.TelemetryMessages;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.vehicle.application.VehicleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Follows the odometers sent by the connected vehicles: the mileage of the register, and therefore the services due
 * by mileage, stay up to date without anybody typing a reading.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.telemetry-mileage.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryMileageListener {

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryMileageListener.class);

    private final VehicleService vehicles;
    private final JsonMapper json;

    public TelemetryMileageListener(VehicleService vehicles, JsonMapper json) {
        this.vehicles = vehicles;
        this.json = json;
    }

    @KafkaListener(topics = TelemetryMessages.TOPIC_MILEAGE, groupId = "vehicle-service")
    public void onMileage(String message) {
        MileageReport report;
        try {
            report = json.readValue(message, MileageReport.class);
        } catch (JacksonException e) {
            LOG.warn("Unreadable odometer reading skipped: {}", e.getOriginalMessage());
            return;
        }
        if (vehicles.recordTelemetryMileage(report.vehicleId(), report.odometerKm(), report.at())) {
            LOG.debug("Odometer of {} updated by telemetry: {} km", report.registration(), report.odometerKm());
        }
    }
}
