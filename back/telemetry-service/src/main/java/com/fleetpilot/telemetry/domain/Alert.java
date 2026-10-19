package com.fleetpilot.telemetry.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * An alert raised by the telemetry rules. While it is not resolved, a new occurrence of the same problem on the same
 * vehicle is counted on it instead of raising a new alert: the alert centre shows problems, not noise.
 */
@Entity
@Table(name = "alert")
public class Alert {

    public enum Type {
        /** Driving well above the speed limit of the category. */
        SPEEDING,
        /** Stopped with the engine running for a long time. */
        LONG_IDLE,
        /** Traction battery almost empty. */
        LOW_BATTERY,
        /** Serious trouble code: the vehicle should stop. */
        ENGINE_FAULT,
        /** Trouble code to check at the next visit to the garage. */
        ENGINE_WARNING,
        /** The service mileage is reached within 500 km. */
        SERVICE_DUE
    }

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    public enum Status {
        OPEN,
        ACKNOWLEDGED,
        RESOLVED
    }

    /** Where and on which vehicle the problem happened. */
    public record Context(UUID vehicleId, UUID companyId, String registration, Double latitude, Double longitude) {
        public Context {
            Objects.requireNonNull(vehicleId, "vehicleId");
            Objects.requireNonNull(companyId, "companyId");
        }
    }

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false, updatable = false)
    private UUID vehicleId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false, updatable = false)
    private String registration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private String message;

    @Column(precision = 10, scale = 1)
    private BigDecimal value;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(nullable = false)
    private int occurrences;

    @Column(name = "handled_by")
    private String handledBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    private String comment;

    @Version
    private Long version;

    protected Alert() {
    }

    public Alert(UUID id, Context context, Type type, Severity severity, String message, Double value, Instant at) {
        this.id = Objects.requireNonNull(id);
        this.vehicleId = context.vehicleId();
        this.companyId = context.companyId();
        this.registration = context.registration();
        this.type = Objects.requireNonNull(type);
        this.severity = Objects.requireNonNull(severity);
        this.message = Objects.requireNonNull(message);
        this.value = value == null ? null : BigDecimal.valueOf(value);
        this.latitude = context.latitude() == null ? null : BigDecimal.valueOf(context.latitude());
        this.longitude = context.longitude() == null ? null : BigDecimal.valueOf(context.longitude());
        this.status = Status.OPEN;
        this.raisedAt = at;
        this.lastSeenAt = at;
        this.occurrences = 1;
    }

    /** The same problem happened again: counted, the latest value and position kept. */
    public void repeat(String newMessage, Double newValue, Context context, Instant at) {
        this.occurrences++;
        this.lastSeenAt = at;
        this.message = newMessage;
        this.value = newValue == null ? value : BigDecimal.valueOf(newValue);
        if (context.latitude() != null) {
            this.latitude = BigDecimal.valueOf(context.latitude());
            this.longitude = BigDecimal.valueOf(context.longitude());
        }
    }

    /** Someone takes the alert in charge (calls the driver, books the garage). */
    public void acknowledge(String by, Instant at) {
        if (status != Status.OPEN) {
            throw new BusinessRuleException("ALERT_NOT_OPEN", "Only an open alert can be acknowledged");
        }
        this.status = Status.ACKNOWLEDGED;
        this.handledBy = by;
        this.acknowledgedAt = at;
    }

    public void resolve(String by, String resolution, Instant at) {
        if (status == Status.RESOLVED) {
            throw new BusinessRuleException("ALERT_RESOLVED", "This alert is already resolved");
        }
        this.status = Status.RESOLVED;
        this.handledBy = by;
        this.resolvedAt = at;
        this.comment = resolution;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getRegistration() {
        return registration;
    }

    public Type getType() {
        return type;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public BigDecimal getValue() {
        return value;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public int getOccurrences() {
        return occurrences;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public Instant getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getComment() {
        return comment;
    }

    public Long getVersion() {
        return version;
    }
}
