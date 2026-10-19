package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.vehicle.application.command.DriverCommands.Assign;
import com.fleetpilot.vehicle.application.command.DriverCommands.EndAssignment;
import com.fleetpilot.vehicle.application.view.Views.AssignmentView;
import com.fleetpilot.vehicle.domain.event.DomainEventPublisher;
import com.fleetpilot.vehicle.domain.event.FleetEvents;
import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.MileageSource;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hands vehicles over to drivers and takes them back.
 *
 * <p>Rules: the vehicle must be in service and free, the driver active, with a valid licence covering the vehicle
 * category, and a driver has at most one permanent vehicle (temporary pool vehicles come on top).
 */
@Service
@Transactional
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final DomainEventPublisher events;
    private final TenantScope tenant;
    private final Clock clock;
    private final AuditLog audit;

    public AssignmentService(AssignmentRepository assignments, VehicleService vehicleService,
            DriverService driverService, DomainEventPublisher events, TenantScope tenant, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.assignments = assignments;
        this.vehicleService = vehicleService;
        this.driverService = driverService;
        this.events = events;
        this.tenant = tenant;
        this.clock = clock;
    }

    public AssignmentView assign(UUID vehicleId, Assign cmd) {
        Vehicle vehicle = vehicleService.load(vehicleId);
        Driver driver = driverService.load(cmd.driverId());
        Assignment.Kind kind = Objects.requireNonNullElse(cmd.kind(), Assignment.Kind.PERMANENT);
        LocalDate today = LocalDate.now(clock);
        LocalDate start = Objects.requireNonNullElse(cmd.startDate(), today);

        if (!driver.getCompanyId().equals(vehicle.getCompanyId())) {
            throw new BusinessRuleException("DRIVER_OF_ANOTHER_COMPANY", "The driver does not work for the vehicle's company");
        }
        if (start.isAfter(today.plusDays(30))) {
            throw new BusinessRuleException("ASSIGNMENT_TOO_FAR_AHEAD", "An assignment can start at most 30 days ahead");
        }
        if (!vehicle.isAssignable()) {
            throw new BusinessRuleException("VEHICLE_NOT_AVAILABLE",
                    "Vehicle " + vehicle.getRegistration() + " is " + vehicle.getStatus() + " and cannot be assigned");
        }
        if (!driver.isActive()) {
            throw new BusinessRuleException("DRIVER_INACTIVE", driver.getFullName() + " is no longer an active driver");
        }
        if (!driver.canDrive(vehicle.getCategory(), start)) {
            throw new BusinessRuleException("LICENSE_NOT_VALID", driver.getFullName()
                    + " does not hold a valid licence for a " + vehicle.getCategory().frenchCode() + " vehicle");
        }
        assignments.findByVehicleIdAndEndDateIsNull(vehicleId).ifPresent(current -> {
            throw new BusinessRuleException("VEHICLE_ALREADY_ASSIGNED", "Vehicle " + vehicle.getRegistration()
                    + " is already assigned to " + current.getDriver().getFullName());
        });
        if (kind == Assignment.Kind.PERMANENT) {
            assignments.findByDriverIdAndEndDateIsNullAndKind(driver.getId(), Assignment.Kind.PERMANENT)
                    .ifPresent(current -> {
                        throw new BusinessRuleException("DRIVER_ALREADY_HAS_VEHICLE", driver.getFullName()
                                + " already drives " + current.getVehicle().getRegistration() + " permanently");
                    });
        }

        Assignment assignment = assignments.save(new Assignment(UUID.randomUUID(), vehicle, driver, kind, start));
        events.publish(EventTypes.DRIVER_ASSIGNED, vehicle.getCompanyId(), "vehicle", vehicleId,
                new FleetEvents.DriverAssigned(assignment.getId(), vehicleId, vehicle.getRegistration(), driver.getId(),
                        driver.getFullName(), kind, start));
        audit.record("DRIVER_ASSIGNED", "VEHICLE", vehicleId, vehicle.getRegistration(), vehicle.getCompanyId(),
                driver.getFullName() + " (" + kind + ")");
        return AssignmentView.of(assignment);
    }

    public AssignmentView end(UUID assignmentId, EndAssignment cmd) {
        Assignment assignment = load(assignmentId);
        Vehicle vehicle = assignment.getVehicle();
        LocalDate endDate = Objects.requireNonNullElse(cmd.endDate(), LocalDate.now(clock));
        int endMileage = Objects.requireNonNullElse(cmd.endMileage(), vehicle.getMileage());
        if (endMileage > vehicle.getMileage()) {
            vehicleService.recordMileage(vehicle, endMileage, clock.instant(), MileageSource.DRIVER);
        }
        assignment.end(endDate, endMileage);
        events.publish(EventTypes.DRIVER_UNASSIGNED, vehicle.getCompanyId(), "vehicle", vehicle.getId(),
                new FleetEvents.DriverUnassigned(assignmentId, vehicle.getId(), vehicle.getRegistration(),
                        assignment.getDriver().getId(), endDate, assignment.drivenKilometres(vehicle.getMileage())));
        audit.record("VEHICLE_RETURNED", "VEHICLE", vehicle.getId(), vehicle.getRegistration(), vehicle.getCompanyId(),
                assignment.getDriver().getFullName() + ", " + endMileage + " km");
        return AssignmentView.of(assignment);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> historyOfVehicle(UUID vehicleId) {
        vehicleService.load(vehicleId);
        return assignments.findByVehicleIdOrderByStartDateDesc(vehicleId).stream().map(AssignmentView::of).toList();
    }

    private Assignment load(UUID id) {
        Assignment assignment = assignments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Assignment", id));
        tenant.checkAccess(assignment.getVehicle().getCompanyId(), "Assignment", id);
        return assignment;
    }
}
