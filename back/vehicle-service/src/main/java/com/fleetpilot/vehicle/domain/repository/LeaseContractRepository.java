package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.LeaseContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeaseContractRepository extends JpaRepository<LeaseContract, UUID>,
        JpaSpecificationExecutor<LeaseContract> {

    Optional<LeaseContract> findByVehicleIdAndStatus(UUID vehicleId, LeaseContract.Status status);

    List<LeaseContract> findByVehicleIdOrderByStartDateDesc(UUID vehicleId);

    boolean existsByContractNumber(String contractNumber);

    @Override
    @EntityGraph(attributePaths = "vehicle")
    Page<LeaseContract> findAll(Specification<LeaseContract> spec, Pageable pageable);
}
