package com.fleetpilot.vehicle.domain.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** History of odometer readings of a vehicle, from every source. */
@Entity
@Table(name = "mileage_reading")
public class MileageReading {

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(nullable = false)
    private int mileage;

    @Column(name = "read_at", nullable = false)
    private Instant readAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MileageSource source;

    protected MileageReading() {
    }

    public MileageReading(UUID id, UUID vehicleId, int mileage, Instant readAt, MileageSource source) {
        this.id = id;
        this.vehicleId = vehicleId;
        this.mileage = mileage;
        this.readAt = readAt;
        this.source = source;
    }

    public UUID getId() {
        return id;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public int getMileage() {
        return mileage;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public MileageSource getSource() {
        return source;
    }
}
