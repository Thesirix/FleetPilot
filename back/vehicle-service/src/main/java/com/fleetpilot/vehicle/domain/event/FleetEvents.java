package com.fleetpilot.vehicle.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.AlertLevel;
import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;

/**
 * Payloads of the business events published by the fleet module on the {@code fleet.events} topic.
 * They are part of the public contract with the other services: fields are only ever added, never renamed.
 */
public final class FleetEvents {

    private FleetEvents() {
    }

    public record VehicleCreated(UUID vehicleId, String registration, VehicleCategory category, Energy energy,
            String make, String model, UUID siteId, int mileage) {
    }

    public record VehicleUpdated(UUID vehicleId, String registration) {
    }

    public record VehicleStatusChanged(UUID vehicleId, String registration, VehicleStatus from, VehicleStatus to) {
    }

    public record DriverAssigned(UUID assignmentId, UUID vehicleId, String registration, UUID driverId,
            String driverName, Assignment.Kind kind, LocalDate startDate) {
    }

    public record DriverUnassigned(UUID assignmentId, UUID vehicleId, String registration, UUID driverId,
            LocalDate endDate, int drivenKilometres) {
    }

    public record DeadlineDue(UUID deadlineId, UUID vehicleId, String registration, DeadlineType type,
            LocalDate dueDate, Integer dueMileage, int currentMileage, AlertLevel level) {
    }

    public record IncidentDeclared(UUID incidentId, String reference, UUID vehicleId, String registration,
            UUID driverId, Incident.Type type, Instant occurredAt, boolean injuries) {
    }

    public record ContractMileageOverrun(UUID contractId, String contractNumber, UUID vehicleId, String registration,
            String lessor, int contractualMileage, int projectedMileage, int projectedOverrunKm, LocalDate endDate) {
    }
}
