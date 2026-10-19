package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.common.event.EventTypes;
import com.fleetpilot.common.util.RegistrationNumbers;
import com.fleetpilot.common.util.Vins;
import com.fleetpilot.vehicle.application.command.VehicleCommands.ChangeStatus;
import com.fleetpilot.vehicle.application.command.VehicleCommands.CreateVehicle;
import com.fleetpilot.vehicle.application.command.VehicleCommands.RecordMileage;
import com.fleetpilot.vehicle.application.command.VehicleCommands.UpdateVehicle;
import com.fleetpilot.vehicle.application.view.Views.AssignmentView;
import com.fleetpilot.vehicle.application.view.Views.CompanyRef;
import com.fleetpilot.vehicle.application.view.Views.ContractView;
import com.fleetpilot.vehicle.application.view.Views.CostCenterRef;
import com.fleetpilot.vehicle.application.view.Views.DeadlineView;
import com.fleetpilot.vehicle.application.view.Views.MileageView;
import com.fleetpilot.vehicle.application.view.Views.SiteRef;
import com.fleetpilot.vehicle.application.view.Views.VehicleDetail;
import com.fleetpilot.vehicle.application.view.Views.VehicleSummary;
import com.fleetpilot.vehicle.domain.event.DomainEventPublisher;
import com.fleetpilot.vehicle.domain.event.FleetEvents;
import com.fleetpilot.vehicle.domain.model.Assignment;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.CostCenter;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.DeadlinePolicy;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.MileageReading;
import com.fleetpilot.vehicle.domain.model.MileageSource;
import com.fleetpilot.vehicle.domain.model.Site;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;
import com.fleetpilot.vehicle.domain.repository.AssignmentRepository;
import com.fleetpilot.vehicle.domain.repository.CompanyRepository;
import com.fleetpilot.vehicle.domain.repository.CostCenterRepository;
import com.fleetpilot.vehicle.domain.repository.DeadlineRepository;
import com.fleetpilot.vehicle.domain.repository.FleetSpecifications;
import com.fleetpilot.vehicle.domain.repository.IncidentRepository;
import com.fleetpilot.vehicle.domain.repository.LeaseContractRepository;
import com.fleetpilot.vehicle.domain.repository.MileageReadingRepository;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.IncidentCriteria;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.VehicleCriteria;
import com.fleetpilot.vehicle.domain.repository.SiteRepository;
import com.fleetpilot.vehicle.domain.repository.VehicleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases of the vehicle register: search, file, entry into the fleet, changes, odometer readings. */
@Service
@Transactional
public class VehicleService {

    static final String RESOURCE = "Vehicle";
    /** A telemetry odometer is kept in the history every 20 km, not every minute. */
    static final int TELEMETRY_STEP_KM = 20;

    private final VehicleRepository vehicles;
    private final CompanyRepository companies;
    private final SiteRepository sites;
    private final CostCenterRepository costCenters;
    private final AssignmentRepository assignments;
    private final LeaseContractRepository contracts;
    private final DeadlineRepository deadlines;
    private final IncidentRepository incidents;
    private final MileageReadingRepository mileageReadings;
    private final DomainEventPublisher events;
    private final TenantScope tenant;
    private final Clock clock;
    private final AuditLog audit;

    public VehicleService(VehicleRepository vehicles, CompanyRepository companies, SiteRepository sites,
            CostCenterRepository costCenters, AssignmentRepository assignments, LeaseContractRepository contracts,
            DeadlineRepository deadlines, IncidentRepository incidents, MileageReadingRepository mileageReadings,
            DomainEventPublisher events, TenantScope tenant, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.vehicles = vehicles;
        this.companies = companies;
        this.sites = sites;
        this.costCenters = costCenters;
        this.assignments = assignments;
        this.contracts = contracts;
        this.deadlines = deadlines;
        this.incidents = incidents;
        this.mileageReadings = mileageReadings;
        this.events = events;
        this.tenant = tenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<VehicleSummary> search(VehicleCriteria criteria, Pageable pageable) {
        VehicleCriteria scoped = criteria.withCompany(tenant.companyFilter(criteria.companyId()));
        Page<Vehicle> page = vehicles.findAll(FleetSpecifications.vehicles(scoped), pageable);
        List<UUID> ids = page.getContent().stream().map(Vehicle::getId).toList();
        Map<UUID, Driver> currentDrivers = ids.isEmpty() ? Map.of()
                : assignments.findByVehicleIdInAndEndDateIsNull(ids).stream()
                        .collect(Collectors.toMap(a -> a.getVehicle().getId(), Assignment::getDriver));
        return page.map(v -> VehicleSummary.of(v, currentDrivers.get(v.getId())));
    }

    @Transactional(readOnly = true)
    public VehicleDetail get(UUID id) {
        return toDetail(load(id));
    }

    @Transactional(readOnly = true)
    public VehicleDetail getByRegistration(String registration) {
        String normalized = RegistrationNumbers.normalize(registration)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, registration));
        Vehicle vehicle = vehicles.findByRegistration(normalized)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE, registration));
        tenant.checkAccess(vehicle.getCompanyId(), RESOURCE, registration);
        checkLessor(vehicle, registration);
        return toDetail(vehicle);
    }

    public VehicleDetail create(CreateVehicle cmd) {
        tenant.checkAccess(cmd.companyId(), "Company", cmd.companyId());
        Company company = companies.findById(cmd.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Company", cmd.companyId()));
        String registration = normalizeRegistration(cmd.registration());
        String vin = Vins.normalize(cmd.vin())
                .orElseThrow(() -> new BusinessRuleException("VIN_INVALID", "Invalid VIN: " + cmd.vin()));
        if (vehicles.existsByRegistration(registration)) {
            throw new ConflictException("REGISTRATION_ALREADY_USED", "A vehicle registered " + registration + " already exists");
        }
        if (vehicles.existsByVin(vin)) {
            throw new ConflictException("VIN_ALREADY_USED", "A vehicle with VIN " + vin + " already exists");
        }
        Instant now = clock.instant();
        Vehicle.Specification spec = new Vehicle.Specification(cmd.category(), cmd.make().trim(), cmd.model().trim(),
                trimToNull(cmd.trimLevel()), cmd.energy(), cmd.critAir(), cmd.co2GramsPerKm(), cmd.fiscalPower(),
                cmd.firstRegistrationDate());
        VehicleStatus status = Objects.requireNonNullElse(cmd.status(), VehicleStatus.ACTIVE);
        if (status == VehicleStatus.DISPOSED) {
            throw new BusinessRuleException("INVALID_INITIAL_STATUS", "A new vehicle cannot be created as disposed");
        }
        Vehicle vehicle = new Vehicle(UUID.randomUUID(), company, registration, vin, spec, cmd.acquisition(),
                cmd.mileage(), now, status);
        vehicle.relocate(findSite(cmd.siteId()), findCostCenter(cmd.costCenterId()));
        vehicles.save(vehicle);
        mileageReadings.save(new MileageReading(UUID.randomUUID(), vehicle.getId(), cmd.mileage(), now, MileageSource.MANAGER));

        LocalDate today = LocalDate.now(clock);
        DeadlinePolicy.initialSchedule(vehicle.getCategory(), vehicle.getEnergy(), vehicle.getFirstRegistrationDate(),
                        vehicle.getMileage(), today)
                .forEach(due -> deadlines.save(new Deadline(UUID.randomUUID(), vehicle, due.type(), due.dueDate(),
                        due.dueMileage())));

        events.publish(EventTypes.VEHICLE_CREATED, company.getId(), "vehicle", vehicle.getId(),
                new FleetEvents.VehicleCreated(vehicle.getId(), registration, vehicle.getCategory(), vehicle.getEnergy(),
                        vehicle.getMake(), vehicle.getModel(), vehicle.getSite() == null ? null : vehicle.getSite().getId(),
                        vehicle.getMileage()));
        audit.record("VEHICLE_CREATED", "VEHICLE", vehicle.getId(), registration, company.getId(),
                vehicle.getMake() + " " + vehicle.getModel());
        return toDetail(vehicle);
    }

    public VehicleDetail update(UUID id, UpdateVehicle cmd) {
        Vehicle vehicle = load(id);
        if (!Objects.equals(cmd.version(), vehicle.getVersion())) {
            throw new ConflictException("STALE_VERSION",
                    "The vehicle was modified by someone else in the meantime. Reload it and try again.");
        }
        String registration = normalizeRegistration(cmd.registration());
        if (!registration.equals(vehicle.getRegistration())) {
            if (vehicles.existsByRegistrationAndIdNot(registration, id)) {
                throw new ConflictException("REGISTRATION_ALREADY_USED",
                        "A vehicle registered " + registration + " already exists");
            }
            vehicle.changeRegistration(registration);
        }
        vehicle.applySpecification(new Vehicle.Specification(cmd.category(), cmd.make().trim(), cmd.model().trim(),
                trimToNull(cmd.trimLevel()), cmd.energy(), cmd.critAir(), cmd.co2GramsPerKm(), cmd.fiscalPower(),
                cmd.firstRegistrationDate()));
        vehicle.relocate(findSite(cmd.siteId()), findCostCenter(cmd.costCenterId()));
        vehicles.saveAndFlush(vehicle);
        events.publish(EventTypes.VEHICLE_UPDATED, vehicle.getCompanyId(), "vehicle", id,
                new FleetEvents.VehicleUpdated(id, registration));
        audit.record("VEHICLE_UPDATED", "VEHICLE", id, registration, vehicle.getCompanyId(), null);
        return toDetail(vehicle);
    }

    public VehicleDetail changeStatus(UUID id, ChangeStatus cmd) {
        Vehicle vehicle = load(id);
        VehicleStatus from = vehicle.getStatus();
        if (cmd.status() == VehicleStatus.DISPOSED) {
            if (assignments.findByVehicleIdAndEndDateIsNull(id).isPresent()) {
                throw new BusinessRuleException("VEHICLE_STILL_ASSIGNED",
                        "End the current assignment before taking the vehicle out of the fleet");
            }
            if (contracts.findByVehicleIdAndStatus(id, LeaseContract.Status.ACTIVE).isPresent()) {
                throw new BusinessRuleException("CONTRACT_STILL_ACTIVE",
                        "End the lease contract before taking the vehicle out of the fleet");
            }
        }
        vehicle.changeStatus(cmd.status());
        if (vehicle.getStatus() == VehicleStatus.DISPOSED) {
            deadlines.findByVehicleIdAndStatusOrderByDueDateAsc(id, Deadline.Status.OPEN).forEach(Deadline::cancel);
        }
        if (from != vehicle.getStatus()) {
            events.publish(EventTypes.VEHICLE_STATUS_CHANGED, vehicle.getCompanyId(), "vehicle", id,
                    new FleetEvents.VehicleStatusChanged(id, vehicle.getRegistration(), from, vehicle.getStatus()));
            audit.record("VEHICLE_STATUS_CHANGED", "VEHICLE", id, vehicle.getRegistration(), vehicle.getCompanyId(),
                    from + " > " + vehicle.getStatus());
        }
        return toDetail(vehicle);
    }

    public VehicleDetail recordMileage(UUID id, RecordMileage cmd) {
        Vehicle vehicle = load(id);
        checkLessor(vehicle, id);
        Instant readAt = Objects.requireNonNullElseGet(cmd.readAt(), clock::instant);
        MileageSource source = Objects.requireNonNullElse(cmd.source(), MileageSource.MANAGER);
        recordMileage(vehicle, cmd.mileage(), readAt, source);
        audit.record("MILEAGE_RECORDED", "VEHICLE", id, vehicle.getRegistration(), vehicle.getCompanyId(),
                cmd.mileage() + " km (" + source + ")");
        return toDetail(vehicle);
    }

    /**
     * Odometer sent by the box of a connected vehicle (system call, no user). Kept when the vehicle has driven at least
     * {@value #TELEMETRY_STEP_KM} km since the last reading, so that the history shows the trend without a reading a
     * minute; an older or lower reading, or a vehicle that left the fleet, is ignored.
     *
     * @return true when the reading was recorded
     */
    public boolean recordTelemetryMileage(UUID vehicleId, int mileage, Instant readAt) {
        return vehicles.findById(vehicleId)
                .filter(v -> v.getStatus() != VehicleStatus.DISPOSED)
                .filter(v -> mileage >= v.getMileage() + TELEMETRY_STEP_KM && !readAt.isBefore(v.getMileageReadAt()))
                .map(v -> {
                    recordMileage(v, mileage, readAt, MileageSource.TELEMETRY);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Odometer typed by the driver with a fill-up or an inspection. A reading older than the last known one (sent late
     * by the offline queue of the app, after the box reported a newer odometer) stays with its record but does not move
     * the register; a lower reading of the same moment is a typing error and is refused.
     */
    void recordDriverReading(Vehicle vehicle, int mileage, Instant readAt) {
        if (!readAt.isBefore(vehicle.getMileageReadAt())) {
            recordMileage(vehicle, mileage, readAt, MileageSource.DRIVER);
        }
    }

    /** Shared with the other use cases that learn a new mileage (vehicle return, completed service...). */
    void recordMileage(Vehicle vehicle, int mileage, Instant readAt, MileageSource source) {
        vehicle.recordMileage(mileage, readAt);
        // The same value from the same source again (a reading sent twice by a phone without network) adds nothing
        boolean repeated = mileageReadings.findFirstByVehicleIdOrderByReadAtDesc(vehicle.getId())
                .filter(last -> last.getMileage() == mileage && last.getSource() == source)
                .isPresent();
        if (!repeated) {
            mileageReadings.save(new MileageReading(UUID.randomUUID(), vehicle.getId(), mileage, readAt, source));
        }
    }

    @Transactional(readOnly = true)
    public List<MileageView> mileageHistory(UUID id) {
        checkLessor(load(id), id);
        return mileageReadings.findTop24ByVehicleIdOrderByReadAtDesc(id).stream().map(MileageView::of).toList();
    }

    /** A lessor only reaches the vehicles it currently leases; any other vehicle does not exist for it. */
    private void checkLessor(Vehicle vehicle, Object id) {
        tenant.restrictedLessor().ifPresent(lessor -> {
            boolean leased = contracts.findByVehicleIdAndStatus(vehicle.getId(), LeaseContract.Status.ACTIVE)
                    .filter(contract -> lessor.equalsIgnoreCase(contract.getLessor()))
                    .isPresent();
            if (!leased) {
                throw new ResourceNotFoundException(RESOURCE, id);
            }
        });
    }

    Vehicle load(UUID id) {
        Vehicle vehicle = vehicles.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
        tenant.checkAccess(vehicle.getCompanyId(), RESOURCE, id);
        return vehicle;
    }

    private VehicleDetail toDetail(Vehicle v) {
        LocalDate today = LocalDate.now(clock);
        AssignmentView current = assignments.findByVehicleIdAndEndDateIsNull(v.getId())
                .map(AssignmentView::of).orElse(null);
        ContractView contract = contracts.findByVehicleIdAndStatus(v.getId(), LeaseContract.Status.ACTIVE)
                .map(c -> ContractView.of(c, today)).orElse(null);
        List<DeadlineView> open = deadlines.findByVehicleIdAndStatusOrderByDueDateAsc(v.getId(), Deadline.Status.OPEN)
                .stream().map(d -> DeadlineView.of(d, today)).toList();
        List<MileageView> history = mileageReadings.findTop24ByVehicleIdOrderByReadAtDesc(v.getId()).stream()
                .map(MileageView::of).toList();
        long openIncidents = incidents.count(FleetSpecifications.incidents(
                new IncidentCriteria(null, v.getId(), null, null, Boolean.TRUE)));
        return new VehicleDetail(v.getId(), CompanyRef.of(v.getCompany()), v.getRegistration(), v.getVin(),
                v.getCategory(), v.getMake(), v.getModel(), v.getTrimLevel(), v.getEnergy(), v.getCritAir(),
                v.getCo2GramsPerKm(), v.getFiscalPower(), v.getFirstRegistrationDate(), v.getAcquisition(),
                v.getMileage(), v.getMileageReadAt(), v.getStatus(), v.getStatus().allowedTransitions(),
                SiteRef.of(v.getSite()), CostCenterRef.of(v.getCostCenter()), current, contract, open, history,
                openIncidents, v.getVersion() == null ? 0 : v.getVersion(), v.getCreatedAt(), v.getUpdatedAt());
    }

    private Site findSite(UUID siteId) {
        return siteId == null ? null
                : sites.findById(siteId).orElseThrow(() -> new ResourceNotFoundException("Site", siteId));
    }

    private CostCenter findCostCenter(UUID costCenterId) {
        return costCenterId == null ? null
                : costCenters.findById(costCenterId).orElseThrow(() -> new ResourceNotFoundException("Cost center", costCenterId));
    }

    private static String normalizeRegistration(String raw) {
        return RegistrationNumbers.normalize(raw).orElseThrow(() -> new BusinessRuleException("REGISTRATION_INVALID",
                "Invalid registration number: " + raw));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
