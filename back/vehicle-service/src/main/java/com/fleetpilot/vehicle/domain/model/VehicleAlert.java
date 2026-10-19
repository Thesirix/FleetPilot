package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An alert raised by the box of a connected vehicle, copied from the telemetry events so that the driver of the
 * vehicle sees it in the app. Type and severity are kept as the telemetry service names them.
 */
@Entity
@Table(name = "vehicle_alert")
public class VehicleAlert {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String message;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    protected VehicleAlert() {
    }

    public VehicleAlert(UUID id, UUID vehicleId, String type, String severity, String message, Instant raisedAt) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.type = type;
        this.severity = severity;
        this.message = message.length() > 300 ? message.substring(0, 300) : message;
        this.raisedAt = raisedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getType() {
        return type;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }
}
