package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.UUID;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.vehicle.application.command.FleetCommands.AssessIncident;
import com.fleetpilot.vehicle.application.command.FleetCommands.ChangeIncidentStatus;
import com.fleetpilot.vehicle.application.command.FleetCommands.DeclareIncident;
import com.fleetpilot.vehicle.application.view.Views.IncidentView;
import com.fleetpilot.vehicle.domain.event.DomainEventPublisher;
import com.fleetpilot.vehicle.domain.event.FleetEvents;
import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.repository.AssignmentRepository;
import com.fleetpilot.vehicle.domain.repository.FleetSpecifications;
import com.fleetpilot.vehicle.domain.repository.IncidentRepository;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.IncidentCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims (sinistres): declaration by a driver or a manager, assessment, follow-up until closure. */
@Service
@Transactional
public class IncidentService {

    private final IncidentRepository incidents;
    private final AssignmentRepository assignments;
    private final VehicleService vehicleService;
    private final DriverService driverService;
    private final DomainEventPublisher events;
    private final TenantScope tenant;
    private final Clock clock;
    private final AuditLog audit;

    public IncidentService(IncidentRepository incidents, AssignmentRepository assignments, VehicleService vehicleService,
            DriverService driverService, DomainEventPublisher events, TenantScope tenant, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.incidents = incidents;
        this.assignments = assignments;
        this.vehicleService = vehicleService;
        this.driverService = driverService;
        this.events = events;
        this.tenant = tenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<IncidentView> search(IncidentCriteria criteria, Pageable pageable) {
        IncidentCriteria scoped = criteria.withCompany(tenant.companyFilter(criteria.companyId()));
        return incidents.findAll(FleetSpecifications.incidents(scoped), pageable).map(IncidentView::of);
    }

    @Transactional(readOnly = true)
    public IncidentView get(UUID id) {
        return IncidentView.of(load(id));
    }

    /** Declares a claim. Without an explicit driver, the vehicle's current driver is used. */
    public IncidentView declare(UUID vehicleId, DeclareIncident cmd) {
        return declare(UUID.randomUUID(), vehicleId, cmd);
    }

    /** Same, with the id chosen by the caller: the mobile app generates it so a replayed declaration is recognised. */
    public IncidentView declare(UUID id, UUID vehicleId, DeclareIncident cmd) {
        Vehicle vehicle = vehicleService.load(vehicleId);
        Driver driver = cmd.driverId() != null
                ? driverService.load(cmd.driverId())
                : assignments.findByVehicleIdAndEndDateIsNull(vehicleId).map(Assignment::getDriver).orElse(null);
        String reference = "SIN-%d-%06d".formatted(Year.now(clock).getValue(), incidents.nextReferenceNumber());
        Incident incident = incidents.save(new Incident(id, reference, vehicle, driver,
                new Incident.Declaration(cmd.type(), cmd.occurredAt(), cmd.location().trim(), cmd.latitude(),
                        cmd.longitude(), cmd.description().trim(), Boolean.TRUE.equals(cmd.thirdPartyInvolved()),
                        Boolean.TRUE.equals(cmd.injuries())),
                clock.instant()));
        events.publish(EventTypes.INCIDENT_DECLARED, vehicle.getCompanyId(), "vehicle", vehicleId,
                new FleetEvents.IncidentDeclared(incident.getId(), reference, vehicleId, vehicle.getRegistration(),
                        driver == null ? null : driver.getId(), cmd.type(), cmd.occurredAt(), incident.isInjuries()));
        audit.record("INCIDENT_DECLARED", "INCIDENT", incident.getId(), reference, vehicle.getCompanyId(),
                cmd.type() + ", " + vehicle.getRegistration());
        return IncidentView.of(incident);
    }

    public IncidentView assess(UUID id, AssessIncident cmd) {
        Incident incident = load(id);
        incident.assess(cmd.responsibility(), cmd.estimatedCost());
        audit.record("INCIDENT_ASSESSED", "INCIDENT", id, incident.getReference(), incident.getVehicle().getCompanyId(),
                cmd.responsibility() + (cmd.estimatedCost() == null ? "" : ", " + cmd.estimatedCost() + " EUR"));
        return IncidentView.of(incident);
    }

    public IncidentView changeStatus(UUID id, ChangeIncidentStatus cmd) {
        Incident incident = load(id);
        incident.moveTo(cmd.status(), LocalDate.now(clock));
        audit.record("INCIDENT_STATUS_CHANGED", "INCIDENT", id, incident.getReference(),
                incident.getVehicle().getCompanyId(), String.valueOf(cmd.status()));
        return IncidentView.of(incident);
    }

    private Incident load(UUID id) {
        Incident incident = incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Incident", id));
        tenant.checkAccess(incident.getVehicle().getCompanyId(), "Incident", id);
        return incident;
    }
}
