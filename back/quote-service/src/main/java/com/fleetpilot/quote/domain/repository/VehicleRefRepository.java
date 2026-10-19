package com.fleetpilot.quote.domain.repository;

import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.quote.domain.model.VehicleRef;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRefRepository extends JpaRepository<VehicleRef, UUID> {

    Optional<VehicleRef> findByRegistration(String registration);
}
