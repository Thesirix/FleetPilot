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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A vehicle of a client company's fleet. The aggregate guards its own invariants: status transitions and the
 * odometer, which can never go backwards.
 */
@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cost_center_id")
    private CostCenter costCenter;

    @Column(nullable = false, unique = true, length = 9)
    private String registration;

    @Column(nullable = false, unique = true, length = 17)
    private String vin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleCategory category;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(name = "trim_level")
    private String trimLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Energy energy;

    @Column(name = "crit_air", nullable = false)
    private int critAir;

    @Column(name = "co2_g_km", nullable = false)
    private int co2GramsPerKm;

    @Column(name = "fiscal_power")
    private Integer fiscalPower;

    @Column(name = "first_registration_date", nullable = false)
    private LocalDate firstRegistrationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Acquisition acquisition;

    @Column(nullable = false)
    private int mileage;

    @Column(name = "mileage_read_at", nullable = false)
    private Instant mileageReadAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatus status;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vehicle() {
    }

    /** Technical specification of a vehicle, as found on its registration certificate. */
    public record Specification(VehicleCategory category, String make, String model, String trimLevel, Energy energy,
            int critAir, int co2GramsPerKm, Integer fiscalPower, LocalDate firstRegistrationDate) {

        public Specification {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(energy, "energy");
            Objects.requireNonNull(firstRegistrationDate, "firstRegistrationDate");
            if (critAir < 0 || critAir > 5) {
                throw new IllegalArgumentException("Crit'Air must be between 0 (E) and 5");
            }
            if (energy.isZeroEmission() && (critAir != 0 || co2GramsPerKm != 0)) {
                throw new BusinessRuleException("ZERO_EMISSION_INCONSISTENT",
                        "An electric or hydrogen vehicle has a Crit'Air E sticker (0) and emits 0 g/km of CO2");
            }
        }
    }

    public Vehicle(UUID id, Company company, String registration, String vin, Specification spec, Acquisition acquisition,
            int mileage, Instant mileageReadAt, VehicleStatus status) {
        this.id = Objects.requireNonNull(id);
        this.company = Objects.requireNonNull(company);
        this.registration = registration;
        this.vin = vin;
        setSpecification(spec);
        this.acquisition = Objects.requireNonNull(acquisition);
        if (mileage < 0) {
            throw new IllegalArgumentException("mileage must be positive");
        }
        this.mileage = mileage;
        this.mileageReadAt = mileageReadAt;
        this.status = Objects.requireNonNull(status);
    }

    public void applySpecification(Specification spec) {
        ensureNotDisposed();
        setSpecification(spec);
    }

    /** Not overridable: called from the constructor (and entity methods must not be final for Hibernate proxies). */
    private void setSpecification(Specification spec) {
        this.category = spec.category();
        this.make = spec.make();
        this.model = spec.model();
        this.trimLevel = spec.trimLevel();
        this.energy = spec.energy();
        this.critAir = spec.critAir();
        this.co2GramsPerKm = spec.co2GramsPerKm();
        this.fiscalPower = spec.fiscalPower();
        this.firstRegistrationDate = spec.firstRegistrationDate();
    }

    public void changeRegistration(String newRegistration) {
        ensureNotDisposed();
        this.registration = newRegistration;
    }

    public void relocate(Site newSite, CostCenter newCostCenter) {
        ensureNotDisposed();
        if (newSite != null && !newSite.getCompany().getId().equals(getCompanyId())) {
            throw new BusinessRuleException("SITE_OF_ANOTHER_COMPANY", "The site belongs to another company");
        }
        if (newCostCenter != null && !newCostCenter.getCompany().getId().equals(getCompanyId())) {
            throw new BusinessRuleException("COST_CENTER_OF_ANOTHER_COMPANY", "The cost center belongs to another company");
        }
        this.site = newSite;
        this.costCenter = newCostCenter;
    }

    public void changeStatus(VehicleStatus target) {
        if (status == target) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "A vehicle cannot go from " + status + " to " + target);
        }
        this.status = target;
    }

    /**
     * Records an odometer reading. Readings older than the current one are kept for history by the caller but do
     * not change the vehicle; a reading lower than the current mileage at a later date is refused (odometer fraud
     * or typo).
     *
     * @return true when the reading became the vehicle's current mileage
     */
    public boolean recordMileage(int newMileage, Instant readAt) {
        ensureNotDisposed();
        if (readAt.isBefore(mileageReadAt)) {
            return false;
        }
        if (newMileage < mileage) {
            throw new BusinessRuleException("MILEAGE_DECREASE",
                    "The odometer cannot go backwards (current " + mileage + " km, received " + newMileage + " km)");
        }
        this.mileage = newMileage;
        this.mileageReadAt = readAt;
        return true;
    }

    public boolean isAssignable() {
        return status == VehicleStatus.ACTIVE;
    }

    private void ensureNotDisposed() {
        if (status == VehicleStatus.DISPOSED) {
            throw new BusinessRuleException("VEHICLE_DISPOSED", "Vehicle " + registration + " has left the fleet");
        }
    }

    public UUID getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public UUID getCompanyId() {
        return company.getId();
    }

    public Site getSite() {
        return site;
    }

    public CostCenter getCostCenter() {
        return costCenter;
    }

    public String getRegistration() {
        return registration;
    }

    public String getVin() {
        return vin;
    }

    public VehicleCategory getCategory() {
        return category;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getTrimLevel() {
        return trimLevel;
    }

    public Energy getEnergy() {
        return energy;
    }

    public int getCritAir() {
        return critAir;
    }

    public int getCo2GramsPerKm() {
        return co2GramsPerKm;
    }

    public Integer getFiscalPower() {
        return fiscalPower;
    }

    public LocalDate getFirstRegistrationDate() {
        return firstRegistrationDate;
    }

    public Acquisition getAcquisition() {
        return acquisition;
    }

    public int getMileage() {
        return mileage;
    }

    public Instant getMileageReadAt() {
        return mileageReadAt;
    }

    public VehicleStatus getStatus() {
        return status;
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
