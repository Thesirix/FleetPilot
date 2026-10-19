package com.fleetpilot.vehicle.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A claim (sinistre) declared on a vehicle: accident, broken glass, theft... */
@Entity
@Table(name = "incident")
public class Incident {

    public enum Type {
        COLLISION,
        GLASS_BREAKAGE,
        THEFT,
        VANDALISM,
        WEATHER,
        FIRE,
        MECHANICAL_FAILURE
    }

    /** Share of responsibility of our driver, as established with the insurer. */
    public enum Responsibility {
        UNKNOWN,
        NONE,
        PARTIAL,
        FULL
    }

    public enum Status {
        DECLARED,
        UNDER_REVIEW,
        IN_REPAIR,
        CLOSED,
        REJECTED;

        Set<Status> allowedTransitions() {
            return switch (this) {
                case DECLARED -> EnumSet.of(UNDER_REVIEW, REJECTED);
                case UNDER_REVIEW -> EnumSet.of(IN_REPAIR, CLOSED, REJECTED);
                case IN_REPAIR -> EnumSet.of(CLOSED);
                case CLOSED, REJECTED -> EnumSet.noneOf(Status.class);
            };
        }
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String location;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "third_party_involved", nullable = false)
    private boolean thirdPartyInvolved;

    @Column(nullable = false)
    private boolean injuries;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Responsibility responsibility;

    @Column(name = "estimated_cost", precision = 10, scale = 2)
    private BigDecimal estimatedCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    /** What the driver or manager reports. */
    public record Declaration(Type type, Instant occurredAt, String location, BigDecimal latitude, BigDecimal longitude,
            String description, boolean thirdPartyInvolved, boolean injuries) {

        public Declaration {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(description, "description");
        }
    }

    public Incident(UUID id, String reference, Vehicle vehicle, Driver driver, Declaration declaration, Instant now) {
        if (declaration.occurredAt().isAfter(now)) {
            throw new BusinessRuleException("INCIDENT_IN_FUTURE", "An incident cannot be declared in the future");
        }
        if (driver != null && !driver.getCompanyId().equals(vehicle.getCompanyId())) {
            throw new BusinessRuleException("DRIVER_OF_ANOTHER_COMPANY", "The driver belongs to another company");
        }
        this.id = Objects.requireNonNull(id);
        this.reference = Objects.requireNonNull(reference);
        this.vehicle = vehicle;
        this.driver = driver;
        this.type = declaration.type();
        this.occurredAt = declaration.occurredAt();
        this.location = declaration.location();
        this.latitude = declaration.latitude();
        this.longitude = declaration.longitude();
        this.description = declaration.description();
        this.thirdPartyInvolved = declaration.thirdPartyInvolved();
        this.injuries = declaration.injuries();
        this.responsibility = Responsibility.UNKNOWN;
        this.status = Status.DECLARED;
    }

    public void moveTo(Status target, LocalDate today) {
        if (!status.allowedTransitions().contains(target)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "An incident cannot go from " + status + " to " + target);
        }
        this.status = target;
        if (target == Status.CLOSED || target == Status.REJECTED) {
            this.closedOn = today;
        }
    }

    public void assess(Responsibility newResponsibility, BigDecimal newEstimatedCost) {
        if (status == Status.CLOSED || status == Status.REJECTED) {
            throw new BusinessRuleException("INCIDENT_CLOSED", "A closed incident cannot be modified");
        }
        if (newEstimatedCost != null && newEstimatedCost.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_COST", "The estimated cost cannot be negative");
        }
        this.responsibility = Objects.requireNonNull(newResponsibility);
        this.estimatedCost = newEstimatedCost;
    }

    public boolean isOpen() {
        return status != Status.CLOSED && status != Status.REJECTED;
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Driver getDriver() {
        return driver;
    }

    public String getReference() {
        return reference;
    }

    public Type getType() {
        return type;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getLocation() {
        return location;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getDescription() {
        return description;
    }

    public boolean isThirdPartyInvolved() {
        return thirdPartyInvolved;
    }

    public boolean isInjuries() {
        return injuries;
    }

    public Responsibility getResponsibility() {
        return responsibility;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getClosedOn() {
        return closedOn;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
