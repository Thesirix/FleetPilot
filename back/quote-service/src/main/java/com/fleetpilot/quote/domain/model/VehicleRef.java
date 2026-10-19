package com.fleetpilot.quote.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Local copy of the vehicles of the fleet module, kept up to date from the {@code fleet.events} topic. A garage
 * identifies the vehicle by its registration; the quote is then attached to the client company that owns it.
 */
@Entity
@Table(name = "vehicle_ref")
public class VehicleRef {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, unique = true)
    private String registration;

    private String make;

    private String model;

    @Column(nullable = false)
    private String category;

    private String energy;

    private String city;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected VehicleRef() {
    }

    public VehicleRef(UUID id, UUID companyId, String registration, String make, String model, String category,
            String energy, String city, Instant syncedAt) {
        this.id = id;
        this.companyId = companyId;
        this.registration = registration;
        this.make = make;
        this.model = model;
        this.category = category;
        this.energy = energy;
        this.city = city;
        this.syncedAt = syncedAt;
    }

    /** The registration changed in the fleet module (correction of a typing error, new plate). */
    public void renamed(String newRegistration, Instant at) {
        this.registration = newRegistration;
        this.syncedAt = at;
    }

    public TariffClass tariffClass() {
        return TariffClass.ofVehicleCategory(category);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getRegistration() {
        return registration;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public String getCategory() {
        return category;
    }

    public String getEnergy() {
        return energy;
    }

    public String getCity() {
        return city;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
