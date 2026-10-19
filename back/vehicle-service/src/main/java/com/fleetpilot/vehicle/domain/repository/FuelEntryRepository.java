package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.FuelEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuelEntryRepository extends JpaRepository<FuelEntry, UUID> {

    List<FuelEntry> findTop50ByDriverIdOrderByFilledAtDesc(UUID driverId);

    List<FuelEntry> findTop50ByVehicleIdOrderByFilledAtDesc(UUID vehicleId);
}
