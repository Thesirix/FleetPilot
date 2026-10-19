package com.fleetpilot.telemetry.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, UUID>, JpaSpecificationExecutor<Alert> {

    /** The alert of this type not resolved yet on this vehicle, if any (at most one, see the unique index). */
    @Query("select a from Alert a where a.vehicleId = :vehicleId and a.type = :type and a.status <> :resolved")
    Optional<Alert> findUnresolved(UUID vehicleId, Alert.Type type, Alert.Status resolved);

    default Optional<Alert> findUnresolved(UUID vehicleId, Alert.Type type) {
        return findUnresolved(vehicleId, type, Alert.Status.RESOLVED);
    }
}
