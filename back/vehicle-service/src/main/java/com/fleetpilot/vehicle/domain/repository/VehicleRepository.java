package com.fleetpilot.vehicle.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Vehicle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID>, JpaSpecificationExecutor<Vehicle> {

    boolean existsByRegistration(String registration);

    boolean existsByVin(String vin);

    boolean existsByRegistrationAndIdNot(String registration, UUID id);

    Optional<Vehicle> findByRegistration(String registration);

    /** Search with the site and cost center fetched in the same query, to list vehicles without N+1 queries. */
    @Override
    @EntityGraph(attributePaths = {"site", "costCenter"})
    Page<Vehicle> findAll(Specification<Vehicle> spec, Pageable pageable);
}
