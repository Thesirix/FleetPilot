package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    List<Inspection> findTop20ByDriverIdOrderByPerformedAtDesc(UUID driverId);

    List<Inspection> findTop20ByVehicleIdOrderByPerformedAtDesc(UUID vehicleId);
}
