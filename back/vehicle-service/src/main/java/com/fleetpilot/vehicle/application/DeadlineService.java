package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.vehicle.application.command.FleetCommands.CompleteDeadline;
import com.fleetpilot.vehicle.application.view.Views.DeadlineView;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.DeadlinePolicy;
import com.fleetpilot.vehicle.domain.model.MileageSource;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.repository.DeadlineRepository;
import com.fleetpilot.vehicle.domain.repository.FleetSpecifications;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.DeadlineCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Follow-up of the deadlines: list by urgency, completion and scheduling of the next occurrence. */
@Service
@Transactional
public class DeadlineService {

    /** The completed deadline and the next occurrence scheduled by the policy, if any. */
    public record Completion(DeadlineView completed, DeadlineView next) {
    }

    private final DeadlineRepository deadlines;
    private final VehicleService vehicleService;
    private final TenantScope tenant;
    private final Clock clock;
    private final AuditLog audit;

    public DeadlineService(DeadlineRepository deadlines, VehicleService vehicleService, TenantScope tenant, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.deadlines = deadlines;
        this.vehicleService = vehicleService;
        this.tenant = tenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DeadlineView> search(DeadlineCriteria criteria, Pageable pageable) {
        LocalDate today = LocalDate.now(clock);
        DeadlineCriteria scoped = new DeadlineCriteria(tenant.companyFilter(criteria.companyId()), criteria.vehicleId(),
                criteria.type(), criteria.urgency(), criteria.dueBefore(), today);
        return deadlines.findAll(FleetSpecifications.deadlines(scoped), pageable).map(d -> DeadlineView.of(d, today));
    }

    public Completion complete(UUID id, CompleteDeadline cmd) {
        Deadline deadline = load(id);
        Vehicle vehicle = deadline.getVehicle();
        LocalDate today = LocalDate.now(clock);
        LocalDate completedOn = Objects.requireNonNullElse(cmd.completedOn(), today);
        int mileage = Objects.requireNonNullElse(cmd.mileage(), vehicle.getMileage());
        if (mileage < vehicle.getMileage() && completedOn.equals(today)) {
            throw new BusinessRuleException("MILEAGE_DECREASE",
                    "The mileage is lower than the vehicle's current mileage (" + vehicle.getMileage() + " km)");
        }
        if (mileage > vehicle.getMileage()) {
            vehicleService.recordMileage(vehicle, mileage, clock.instant(), MileageSource.GARAGE);
        }
        deadline.complete(completedOn, mileage);
        deadlines.flush();

        Deadline next = DeadlinePolicy.next(deadline.getType(), vehicle.getCategory(), vehicle.getEnergy(), completedOn,
                        mileage)
                .map(due -> deadlines.save(new Deadline(UUID.randomUUID(), vehicle, due.type(), due.dueDate(),
                        due.dueMileage())))
                .orElse(null);
        audit.record("DEADLINE_COMPLETED", "DEADLINE", id, vehicle.getRegistration(), vehicle.getCompanyId(),
                deadline.getType() + ", " + completedOn + ", " + mileage + " km");
        return new Completion(DeadlineView.of(deadline, today), next == null ? null : DeadlineView.of(next, today));
    }

    private Deadline load(UUID id) {
        Deadline deadline = deadlines.findById(id).orElseThrow(() -> new ResourceNotFoundException("Deadline", id));
        tenant.checkAccess(deadline.getVehicle().getCompanyId(), "Deadline", id);
        return deadline;
    }
}
