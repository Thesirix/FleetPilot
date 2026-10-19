package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DeadlineRepository extends JpaRepository<Deadline, UUID>, JpaSpecificationExecutor<Deadline> {

    List<Deadline> findByVehicleIdAndStatusOrderByDueDateAsc(UUID vehicleId, Deadline.Status status);

    Optional<Deadline> findByVehicleIdAndTypeAndStatus(UUID vehicleId, DeadlineType type, Deadline.Status status);

    List<Deadline> findTop10ByVehicleIdAndStatusOrderByCompletedOnDesc(UUID vehicleId, Deadline.Status status);

    /** Open deadlines, page by page, for the daily sweep. */
    @EntityGraph(attributePaths = "vehicle")
    Slice<Deadline> findByStatus(Deadline.Status status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "vehicle")
    Page<Deadline> findAll(Specification<Deadline> spec, Pageable pageable);
}
