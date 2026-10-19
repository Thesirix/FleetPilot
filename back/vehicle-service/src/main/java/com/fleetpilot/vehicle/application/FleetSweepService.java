package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.vehicle.domain.event.DomainEventPublisher;
import com.fleetpilot.vehicle.domain.event.FleetEvents;
import com.fleetpilot.vehicle.domain.model.AlertLevel;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.repository.DeadlineRepository;
import com.fleetpilot.vehicle.domain.repository.FleetSpecifications;
import com.fleetpilot.vehicle.domain.repository.LeaseContractRepository;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.ContractCriteria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily sweep of the whole fleet: sends the J-30 / J-7 / overdue reminders of the deadlines and warns when a lease
 * contract is heading for a mileage overrun. Each reminder is sent once; running the sweep twice is harmless.
 */
@Service
public class FleetSweepService {

    private static final Logger LOG = LoggerFactory.getLogger(FleetSweepService.class);
    private static final int PAGE_SIZE = 500;
    static final String LOCK_NAME = "fleet-daily-sweep";

    /** What one run produced. {@code skipped} is true when another instance was already running the sweep. */
    public record SweepReport(int deadlineAlerts, int overrunAlerts, boolean skipped) {
    }

    private final DeadlineRepository deadlines;
    private final LeaseContractRepository contracts;
    private final DomainEventPublisher events;
    private final ClusterLock lock;
    private final Clock clock;

    public FleetSweepService(DeadlineRepository deadlines, LeaseContractRepository contracts,
            DomainEventPublisher events, ClusterLock lock, Clock clock) {
        this.deadlines = deadlines;
        this.contracts = contracts;
        this.events = events;
        this.lock = lock;
        this.clock = clock;
    }

    @Transactional
    public SweepReport run() {
        if (!lock.tryLock(LOCK_NAME)) {
            LOG.info("Daily sweep already running on another instance, skipped");
            return new SweepReport(0, 0, true);
        }
        LocalDate today = LocalDate.now(clock);
        int deadlineAlerts = sweepDeadlines(today);
        int overrunAlerts = sweepContracts(today);
        LOG.info("Daily sweep done: {} deadline reminders, {} mileage overrun warnings", deadlineAlerts, overrunAlerts);
        return new SweepReport(deadlineAlerts, overrunAlerts, false);
    }

    private int sweepDeadlines(LocalDate today) {
        int sent = 0;
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));
        Slice<Deadline> slice;
        do {
            slice = deadlines.findByStatus(Deadline.Status.OPEN, pageable);
            for (Deadline deadline : slice) {
                Vehicle vehicle = deadline.getVehicle();
                Optional<AlertLevel> level = deadline.alertToSend(today, vehicle.getMileage());
                if (level.isPresent() && vehicle.getStatus().isInFleet()) {
                    deadline.markAlertSent(level.get());
                    events.publish(EventTypes.DEADLINE_DUE, vehicle.getCompanyId(), "vehicle", vehicle.getId(),
                            new FleetEvents.DeadlineDue(deadline.getId(), vehicle.getId(), vehicle.getRegistration(),
                                    deadline.getType(), deadline.getDueDate(), deadline.getDueMileage(),
                                    vehicle.getMileage(), level.get()));
                    sent++;
                }
            }
            pageable = slice.nextPageable();
        } while (slice.hasNext());
        return sent;
    }

    private int sweepContracts(LocalDate today) {
        int sent = 0;
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));
        Page<LeaseContract> page;
        do {
            page = contracts.findAll(FleetSpecifications.contracts(
                    new ContractCriteria(null, null, LeaseContract.Status.ACTIVE, null)), pageable);
            for (LeaseContract contract : page) {
                Vehicle vehicle = contract.getVehicle();
                LeaseContract.MileageProjection projection = contract.projectMileage(vehicle.getMileage(), today);
                if (contract.needsOverrunAlert(projection)) {
                    contract.markOverrunAlerted(today);
                    events.publish(EventTypes.CONTRACT_MILEAGE_OVERRUN, vehicle.getCompanyId(), "vehicle",
                            vehicle.getId(), new FleetEvents.ContractMileageOverrun(contract.getId(),
                                    contract.getContractNumber(), vehicle.getId(), vehicle.getRegistration(),
                                    contract.getLessor(), contract.getContractualMileage(),
                                    projection.projectedMileage(), projection.projectedOverrunKm(),
                                    contract.getEndDate()));
                    sent++;
                }
            }
            pageable = page.nextPageable();
        } while (page.hasNext());
        return sent;
    }
}
