package com.fleetpilot.integration.fleet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What the bus needs to know of the fleet to check the partners' files: the vehicles and the lease contracts. */
public interface FleetDirectory {

    record FleetVehicle(UUID id, UUID companyId, String registration, String category, String make, String model,
            String energy, String status, int mileage) {
    }

    record LeaseContract(UUID id, String lessor, String contractNumber, UUID vehicleId, String registration,
            LocalDate startDate, LocalDate endDate, BigDecimal monthlyRent, String status, LocalDate endedOn) {
    }

    List<FleetVehicle> vehicles();

    default Optional<FleetVehicle> vehicle(String registration) {
        return vehicles().stream().filter(v -> v.registration().equals(registration)).findFirst();
    }

    /** Every contract of a lessor, active or ended. */
    List<LeaseContract> contracts(String lessor);
}
