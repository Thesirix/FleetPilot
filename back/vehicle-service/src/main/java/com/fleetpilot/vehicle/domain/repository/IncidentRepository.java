package com.fleetpilot.vehicle.domain.repository;

import java.util.List;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    @EntityGraph(attributePaths = "driver")
    List<Incident> findByVehicleIdOrderByOccurredAtDesc(UUID vehicleId);

    @EntityGraph(attributePaths = "vehicle")
    List<Incident> findByDriverIdOrderByOccurredAtDesc(UUID driverId);

    @Query(value = "select nextval('incident_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    @Override
    @EntityGraph(attributePaths = {"vehicle", "driver"})
    Page<Incident> findAll(Specification<Incident> spec, Pageable pageable);
}
