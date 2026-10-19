package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.MileageReading;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MileageReadingRepository extends JpaRepository<MileageReading, UUID> {

    List<MileageReading> findTop24ByVehicleIdOrderByReadAtDesc(UUID vehicleId);

    Optional<MileageReading> findFirstByVehicleIdOrderByReadAtDesc(UUID vehicleId);
}
