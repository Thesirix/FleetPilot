package com.fleetpilot.telemetry.application;

import java.util.UUID;

import com.fleetpilot.telemetry.domain.LiveVehicle.VehicleInfo;

/** Category, energy and next service mileage of the vehicles, from the fleet module (kept locally, refreshed). */
public interface FleetReference {

    /** What is known of a vehicle; a vehicle not known yet is treated as a car. */
    VehicleInfo info(UUID vehicleId);
}
