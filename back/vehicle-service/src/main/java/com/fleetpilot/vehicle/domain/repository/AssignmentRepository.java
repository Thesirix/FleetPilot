package com.fleetpilot.vehicle.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Assignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    @EntityGraph(attributePaths = "driver")
    Optional<Assignment> findByVehicleIdAndEndDateIsNull(UUID vehicleId);

    @EntityGraph(attributePaths = "vehicle")
    Optional<Assignment> findByDriverIdAndEndDateIsNullAndKind(UUID driverId, Assignment.Kind kind);

    /** Current drivers of a page of vehicles, in one query. */
    @EntityGraph(attributePaths = "driver")
    List<Assignment> findByVehicleIdInAndEndDateIsNull(Collection<UUID> vehicleIds);

    /** Current vehicles of a page of drivers, in one query. */
    @EntityGraph(attributePaths = "vehicle")
    List<Assignment> findByDriverIdInAndEndDateIsNull(Collection<UUID> driverIds);

    @EntityGraph(attributePaths = "driver")
    List<Assignment> findByVehicleIdOrderByStartDateDesc(UUID vehicleId);

    @EntityGraph(attributePaths = "vehicle")
    List<Assignment> findByDriverIdOrderByStartDateDesc(UUID driverId);
}
