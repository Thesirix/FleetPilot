package com.fleetpilot.common.telemetry;

import java.time.Instant;
import java.util.UUID;

/**
 * Messages sent by the connected vehicles (the boxes installed in the vehicles, played by the vehicle simulator).
 *
 * <p>Unlike the business events, they are raw signals sent every few seconds: compact JSON without envelope, keyed by
 * vehicle id on Kafka so that the messages of one vehicle stay in order.
 */
public final class TelemetryMessages {

    /** Positions, every few seconds while driving, every minute while parked. */
    public static final String TOPIC_POSITION = "telemetry.position";
    /** Odometer readings, every minute while driving and at the end of each trip. */
    public static final String TOPIC_MILEAGE = "telemetry.mileage";
    /** Diagnostic trouble codes reported by the vehicle. */
    public static final String TOPIC_FAULT = "telemetry.fault";

    private TelemetryMessages() {
    }

    /**
     * @param ignition       engine (or motor) on
     * @param batteryPercent state of charge of the traction battery, electric vehicles only
     */
    public record PositionReport(UUID vehicleId, UUID companyId, String registration, Instant at, double latitude,
            double longitude, int speedKmh, int heading, boolean ignition, Integer batteryPercent) {
    }

    public record MileageReport(UUID vehicleId, UUID companyId, String registration, Instant at, int odometerKm) {
    }

    /** Severity of a trouble code, as classified by the box. */
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    /**
     * @param code OBD-II diagnostic trouble code, for example P0300
     */
    public record FaultReport(UUID vehicleId, UUID companyId, String registration, Instant at, String code,
            String description, Severity severity) {
    }
}
