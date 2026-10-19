package com.fleetpilot.vehicle.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.VehicleAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleAlertRepository extends JpaRepository<VehicleAlert, UUID> {

    List<VehicleAlert> findTop20ByVehicleIdAndRaisedAtAfterOrderByRaisedAtDesc(UUID vehicleId, Instant since);
}
