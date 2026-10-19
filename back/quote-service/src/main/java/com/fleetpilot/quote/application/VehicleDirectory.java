package com.fleetpilot.quote.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port to the fleet module (vehicle-service), called with the identity of the quote service itself. */
public interface VehicleDirectory {

    /** A vehicle as described by the fleet module. */
    record RemoteVehicle(UUID id, UUID companyId, String registration, String make, String model, String category,
            String energy, String city) {
    }

    /** Looks a vehicle up by its normalized registration (GH-482-KM). */
    Optional<RemoteVehicle> findByRegistration(String registration);

    /** Every vehicle of every client company, for the initial synchronization. */
    List<RemoteVehicle> findAll();
}
