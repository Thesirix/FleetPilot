package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
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
import org.hibernate.annotations.CreationTimestamp;

/** A period during which a driver is responsible for a vehicle. An open assignment has no end date. */
@Entity
@Table(name = "assignment")
public class Assignment {

    /** Permanent: the driver's own company car. Temporary: pool vehicle or replacement while theirs is at the garage. */
    public enum Kind {
        PERMANENT,
        TEMPORARY
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false, updatable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "start_mileage", nullable = false)
    private int startMileage;

    @Column(name = "end_mileage")
    private Integer endMileage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Assignment() {
    }

    public Assignment(UUID id, Vehicle vehicle, Driver driver, Kind kind, LocalDate startDate) {
        this.id = Objects.requireNonNull(id);
        this.vehicle = Objects.requireNonNull(vehicle);
        this.driver = Objects.requireNonNull(driver);
        this.kind = Objects.requireNonNull(kind);
        this.startDate = Objects.requireNonNull(startDate);
        this.startMileage = vehicle.getMileage();
    }

    /** Closes the assignment when the driver hands the vehicle back. */
    public void end(LocalDate on, int mileageAtReturn) {
        if (!isCurrent()) {
            throw new BusinessRuleException("ASSIGNMENT_ALREADY_ENDED", "This assignment ended on " + endDate);
        }
        if (on.isBefore(startDate)) {
            throw new BusinessRuleException("ASSIGNMENT_END_BEFORE_START",
                    "The vehicle cannot be returned before it was handed over (" + startDate + ")");
        }
        if (mileageAtReturn < startMileage) {
            throw new BusinessRuleException("MILEAGE_DECREASE",
                    "The return mileage is lower than the mileage at handover (" + startMileage + " km)");
        }
        this.endDate = on;
        this.endMileage = mileageAtReturn;
    }

    public boolean isCurrent() {
        return endDate == null;
    }

    /** Kilometres driven during the assignment, or so far for a current one. */
    public int drivenKilometres(int currentVehicleMileage) {
        return (endMileage != null ? endMileage : currentVehicleMileage) - startMileage;
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

    public Kind getKind() {
        return kind;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getStartMileage() {
        return startMileage;
    }

    public Integer getEndMileage() {
        return endMileage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
