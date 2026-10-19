package com.fleetpilot.vehicle.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.error.ConflictException;
import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.vehicle.application.command.FleetCommands.CreateContract;
import com.fleetpilot.vehicle.application.command.FleetCommands.EndContract;
import com.fleetpilot.vehicle.application.view.Views.ContractView;
import com.fleetpilot.vehicle.domain.model.Acquisition;
import com.fleetpilot.vehicle.domain.model.Deadline;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.repository.DeadlineRepository;
import com.fleetpilot.vehicle.domain.repository.FleetSpecifications;
import com.fleetpilot.vehicle.domain.repository.LeaseContractRepository;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.ContractCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Long-term lease contracts: signature, end, follow-up of the contractual mileage. */
@Service
@Transactional
public class ContractService {

    private final LeaseContractRepository contracts;
    private final DeadlineRepository deadlines;
    private final VehicleService vehicleService;
    private final TenantScope tenant;
    private final Clock clock;
    private final AuditLog audit;

    public ContractService(LeaseContractRepository contracts, DeadlineRepository deadlines, VehicleService vehicleService,
            TenantScope tenant, Clock clock, AuditLog audit) {
        this.audit = audit;
        this.contracts = contracts;
        this.deadlines = deadlines;
        this.vehicleService = vehicleService;
        this.tenant = tenant;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ContractView> search(ContractCriteria criteria, Pageable pageable) {
        ContractCriteria scoped = criteria.withCompany(tenant.companyFilter(criteria.companyId()));
        // A lessor only sees its own contracts, whatever it asks for
        scoped = tenant.restrictedLessor().map(scoped::withLessor).orElse(scoped);
        LocalDate today = LocalDate.now(clock);
        return contracts.findAll(FleetSpecifications.contracts(scoped), pageable).map(c -> ContractView.of(c, today));
    }

    @Transactional(readOnly = true)
    public ContractView get(UUID id) {
        LeaseContract contract = load(id);
        tenant.restrictedLessor().filter(lessor -> !lessor.equalsIgnoreCase(contract.getLessor())).ifPresent(other -> {
            throw new ResourceNotFoundException("Contract", id);
        });
        return ContractView.of(contract, LocalDate.now(clock));
    }

    @Transactional(readOnly = true)
    public List<ContractView> ofVehicle(UUID vehicleId) {
        vehicleService.load(vehicleId);
        LocalDate today = LocalDate.now(clock);
        return contracts.findByVehicleIdOrderByStartDateDesc(vehicleId).stream().map(c -> ContractView.of(c, today)).toList();
    }

    public ContractView create(UUID vehicleId, CreateContract cmd) {
        Vehicle vehicle = vehicleService.load(vehicleId);
        if (vehicle.getAcquisition() != Acquisition.LEASE) {
            throw new BusinessRuleException("VEHICLE_NOT_LEASED", "Vehicle " + vehicle.getRegistration()
                    + " is owned by the company and has no lease contract");
        }
        contracts.findByVehicleIdAndStatus(vehicleId, LeaseContract.Status.ACTIVE).ifPresent(active -> {
            throw new BusinessRuleException("CONTRACT_ALREADY_ACTIVE",
                    "Contract " + active.getContractNumber() + " is still active for this vehicle");
        });
        String number = cmd.contractNumber().trim();
        if (contracts.existsByContractNumber(number)) {
            throw new ConflictException("CONTRACT_NUMBER_ALREADY_USED", "Contract " + number + " already exists");
        }
        LeaseContract contract = contracts.save(new LeaseContract(UUID.randomUUID(), vehicle,
                new LeaseContract.Terms(cmd.lessor().trim(), number, cmd.startDate(), cmd.durationMonths(),
                        cmd.contractualMileage(), cmd.monthlyRent(), Boolean.TRUE.equals(cmd.maintenanceIncluded()),
                        Boolean.TRUE.equals(cmd.tyresIncluded()), Boolean.TRUE.equals(cmd.replacementVehicle()))));
        deadlines.findByVehicleIdAndTypeAndStatus(vehicleId, DeadlineType.CONTRACT_END, Deadline.Status.OPEN)
                .ifPresent(Deadline::cancel);
        deadlines.flush();
        deadlines.save(new Deadline(UUID.randomUUID(), vehicle, DeadlineType.CONTRACT_END, contract.getEndDate(), null));
        audit.record("CONTRACT_CREATED", "CONTRACT", contract.getId(), number, vehicle.getCompanyId(),
                contract.getLessor() + ", " + vehicle.getRegistration());
        return ContractView.of(contract, LocalDate.now(clock));
    }

    public ContractView end(UUID id, EndContract cmd) {
        LeaseContract contract = load(id);
        contract.end(Objects.requireNonNullElse(cmd.endedOn(), LocalDate.now(clock)));
        deadlines.findByVehicleIdAndTypeAndStatus(contract.getVehicle().getId(), DeadlineType.CONTRACT_END,
                Deadline.Status.OPEN).ifPresent(Deadline::cancel);
        audit.record("CONTRACT_ENDED", "CONTRACT", id, contract.getContractNumber(),
                contract.getVehicle().getCompanyId(), contract.getVehicle().getRegistration());
        return ContractView.of(contract, LocalDate.now(clock));
    }

    private LeaseContract load(UUID id) {
        LeaseContract contract = contracts.findById(id).orElseThrow(() -> new ResourceNotFoundException("Contract", id));
        tenant.checkAccess(contract.getVehicle().getCompanyId(), "Contract", id);
        return contract;
    }
}
