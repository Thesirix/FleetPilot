package com.fleetpilot.vehicle.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Long-term lease (LLD, location longue durée) of a vehicle: a monthly rent for a fixed duration and mileage.
 *
 * <p>Driving more than the contractual mileage is billed by the lessor at the end of the contract, so FleetPilot
 * projects the mileage at the end date from the real usage and warns early enough to renegotiate the contract.
 */
@Entity
@Table(name = "lease_contract")
public class LeaseContract {

    /** Projected overrun above which a contract should be renegotiated (5 % of the contractual mileage). */
    public static final double OVERRUN_TOLERANCE = 0.05;

    public enum Status {
        ACTIVE,
        ENDED
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, updatable = false)
    private Vehicle vehicle;

    @Column(nullable = false)
    private String lessor;

    @Column(name = "contract_number", nullable = false, unique = true)
    private String contractNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "duration_months", nullable = false)
    private int durationMonths;

    /** Start date plus duration, stored to search contracts ending soon with an index. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "contractual_mileage", nullable = false)
    private int contractualMileage;

    @Column(name = "start_mileage", nullable = false)
    private int startMileage;

    @Column(name = "monthly_rent", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "maintenance_included", nullable = false)
    private boolean maintenanceIncluded;

    @Column(name = "tyres_included", nullable = false)
    private boolean tyresIncluded;

    @Column(name = "replacement_vehicle", nullable = false)
    private boolean replacementVehicle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "overrun_alerted_on")
    private LocalDate overrunAlertedOn;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LeaseContract() {
    }

    /** Commercial terms of a lease. */
    public record Terms(String lessor, String contractNumber, LocalDate startDate, int durationMonths,
            int contractualMileage, BigDecimal monthlyRent, boolean maintenanceIncluded, boolean tyresIncluded,
            boolean replacementVehicle) {

        public Terms {
            Objects.requireNonNull(lessor, "lessor");
            Objects.requireNonNull(contractNumber, "contractNumber");
            Objects.requireNonNull(startDate, "startDate");
            Objects.requireNonNull(monthlyRent, "monthlyRent");
            if (durationMonths < 6 || durationMonths > 84) {
                throw new BusinessRuleException("CONTRACT_DURATION_INVALID", "A lease lasts between 6 and 84 months");
            }
            if (contractualMileage <= 0 || monthlyRent.signum() <= 0) {
                throw new BusinessRuleException("CONTRACT_TERMS_INVALID", "Mileage and rent must be positive");
            }
        }
    }

    /**
     * Mileage forecast at the end of the contract.
     *
     * @param projectedMileage    kilometres expected to be driven over the whole contract at the current pace
     * @param projectedOverrunKm  kilometres above the contractual mileage (negative when under)
     * @param dailyAverageKm      average kilometres per day since the start of the contract
     * @param overrunRisk         true when the overrun exceeds {@link #OVERRUN_TOLERANCE}
     */
    public record MileageProjection(int projectedMileage, int projectedOverrunKm, double dailyAverageKm,
            boolean overrunRisk) {
    }

    public LeaseContract(UUID id, Vehicle vehicle, Terms terms) {
        this.id = Objects.requireNonNull(id);
        this.vehicle = Objects.requireNonNull(vehicle);
        this.lessor = terms.lessor();
        this.contractNumber = terms.contractNumber();
        this.startDate = terms.startDate();
        this.durationMonths = terms.durationMonths();
        this.endDate = terms.startDate().plusMonths(terms.durationMonths());
        this.contractualMileage = terms.contractualMileage();
        this.monthlyRent = terms.monthlyRent().setScale(2, RoundingMode.HALF_UP);
        this.maintenanceIncluded = terms.maintenanceIncluded();
        this.tyresIncluded = terms.tyresIncluded();
        this.replacementVehicle = terms.replacementVehicle();
        this.startMileage = vehicle.getMileage();
        this.status = Status.ACTIVE;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void end(LocalDate on) {
        if (status == Status.ENDED) {
            throw new BusinessRuleException("CONTRACT_ALREADY_ENDED", "Contract " + contractNumber + " is already ended");
        }
        if (on.isBefore(startDate)) {
            throw new BusinessRuleException("CONTRACT_END_BEFORE_START", "A contract cannot end before it starts");
        }
        this.status = Status.ENDED;
        this.endedOn = on;
    }

    /** Projects the mileage at the contract end date from the usage observed so far. */
    public MileageProjection projectMileage(int currentMileage, LocalDate today) {
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(startDate, getEndDate()));
        long elapsedDays = Math.clamp(ChronoUnit.DAYS.between(startDate, today), 1, totalDays);
        int driven = Math.max(0, currentMileage - startMileage);
        double dailyAverage = (double) driven / elapsedDays;
        int projected = (int) Math.round(dailyAverage * totalDays);
        int overrun = projected - contractualMileage;
        boolean risk = overrun > contractualMileage * OVERRUN_TOLERANCE;
        return new MileageProjection(projected, overrun, Math.round(dailyAverage * 10) / 10.0, risk);
    }

    /** True when an overrun risk was detected and has not been reported yet. */
    public boolean needsOverrunAlert(MileageProjection projection) {
        return status == Status.ACTIVE && projection.overrunRisk() && overrunAlertedOn == null;
    }

    public void markOverrunAlerted(LocalDate on) {
        this.overrunAlertedOn = on;
    }

    /** Total rent over the contract duration. */
    public BigDecimal getTotalRent() {
        return monthlyRent.multiply(BigDecimal.valueOf(durationMonths));
    }

    public UUID getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public String getLessor() {
        return lessor;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public int getDurationMonths() {
        return durationMonths;
    }

    public int getContractualMileage() {
        return contractualMileage;
    }

    public int getStartMileage() {
        return startMileage;
    }

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public boolean isMaintenanceIncluded() {
        return maintenanceIncluded;
    }

    public boolean isTyresIncluded() {
        return tyresIncluded;
    }

    public boolean isReplacementVehicle() {
        return replacementVehicle;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getEndedOn() {
        return endedOn;
    }

    public LocalDate getOverrunAlertedOn() {
        return overrunAlertedOn;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
