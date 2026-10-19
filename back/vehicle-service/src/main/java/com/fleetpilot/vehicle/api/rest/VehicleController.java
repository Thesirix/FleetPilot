package com.fleetpilot.vehicle.api.rest;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.AssignmentService;
import com.fleetpilot.vehicle.application.ContractService;
import com.fleetpilot.vehicle.application.IncidentService;
import com.fleetpilot.vehicle.application.VehicleImportService;
import com.fleetpilot.vehicle.application.VehicleImportService.ImportReport;
import com.fleetpilot.vehicle.application.VehicleService;
import com.fleetpilot.vehicle.application.command.DriverCommands.Assign;
import com.fleetpilot.vehicle.application.command.FleetCommands.CreateContract;
import com.fleetpilot.vehicle.application.command.FleetCommands.DeclareIncident;
import com.fleetpilot.vehicle.application.command.VehicleCommands.ChangeStatus;
import com.fleetpilot.vehicle.application.command.VehicleCommands.CreateVehicle;
import com.fleetpilot.vehicle.application.command.VehicleCommands.RecordMileage;
import com.fleetpilot.vehicle.application.command.VehicleCommands.UpdateVehicle;
import com.fleetpilot.vehicle.application.view.Views.AssignmentView;
import com.fleetpilot.vehicle.application.view.Views.ContractView;
import com.fleetpilot.vehicle.application.view.Views.IncidentView;
import com.fleetpilot.vehicle.application.view.Views.MileageView;
import com.fleetpilot.vehicle.application.view.Views.VehicleDetail;
import com.fleetpilot.vehicle.application.view.Views.VehicleSummary;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.VehicleCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/vehicles")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Vehicles", description = "Vehicle register of the client companies")
public class VehicleController {

    private final VehicleService vehicles;
    private final AssignmentService assignments;
    private final ContractService contracts;
    private final IncidentService incidents;
    private final VehicleImportService imports;

    public VehicleController(VehicleService vehicles, AssignmentService assignments, ContractService contracts,
            IncidentService incidents, VehicleImportService imports) {
        this.vehicles = vehicles;
        this.assignments = assignments;
        this.contracts = contracts;
        this.incidents = incidents;
        this.imports = imports;
    }

    @GetMapping
    @Operation(summary = "Search vehicles", description = "Paginated, filterable list. Sort by registration, make, "
            + "model, mileage, status, firstRegistrationDate...")
    public PageResponse<VehicleSummary> search(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) VehicleCategory category,
            @RequestParam(required = false) Energy energy,
            @Parameter(description = "Registration, VIN, make or model") @RequestParam(required = false) String q,
            @Parameter(description = "With (true) or without (false) a current driver")
            @RequestParam(required = false) Boolean assigned,
            @Parameter(description = "Only these vehicles (comma separated ids)")
            @RequestParam(required = false) List<UUID> ids,
            @ParameterObject @PageableDefault(size = 20, sort = "registration", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return PageResponse.of(vehicles.search(
                new VehicleCriteria(companyId, siteId, status, category, energy, q, assigned, ids), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Vehicle file", description = "With current driver, active contract, open deadlines and "
            + "mileage history")
    public VehicleDetail get(@PathVariable UUID id) {
        return vehicles.get(id);
    }

    @GetMapping("/by-registration/{registration}")
    @PreAuthorize(Roles.ANY_MANAGER_OR_SUPPLIER)
    @Operation(summary = "Find a vehicle by registration number", description = "Accepts any spelling: gh482km, GH 482 KM")
    public VehicleDetail getByRegistration(@PathVariable String registration) {
        return vehicles.getByRegistration(registration);
    }

    @PostMapping
    @Operation(summary = "Add a vehicle to the fleet", description = "Its deadlines are scheduled automatically")
    public ResponseEntity<VehicleDetail> create(@Valid @RequestBody CreateVehicle command) {
        VehicleDetail created = vehicles.create(command);
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a vehicle", description = "Optimistic locking: send the version you read")
    public VehicleDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateVehicle command) {
        return vehicles.update(id, command);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change the status", description = "In service, at the garage, immobilized, out of the fleet")
    public VehicleDetail changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatus command) {
        return vehicles.changeStatus(id, command);
    }

    @PostMapping("/{id}/mileage")
    @PreAuthorize(Roles.ANY_MANAGER_OR_SUPPLIER)
    @Operation(summary = "Record an odometer reading")
    public VehicleDetail recordMileage(@PathVariable UUID id, @Valid @RequestBody RecordMileage command) {
        return vehicles.recordMileage(id, command);
    }

    @GetMapping("/{id}/mileage")
    @PreAuthorize(Roles.ANY_MANAGER_OR_SUPPLIER)
    @Operation(summary = "Last odometer readings")
    public List<MileageView> mileage(@PathVariable UUID id) {
        return vehicles.mileageHistory(id);
    }

    @GetMapping("/{id}/assignments")
    @Operation(summary = "Drivers of the vehicle, current and past")
    public List<AssignmentView> assignments(@PathVariable UUID id) {
        return assignments.historyOfVehicle(id);
    }

    @PostMapping("/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Hand the vehicle over to a driver")
    public AssignmentView assign(@PathVariable UUID id, @Valid @RequestBody Assign command) {
        return assignments.assign(id, command);
    }

    @GetMapping("/{id}/contracts")
    @Operation(summary = "Lease contracts of the vehicle")
    public List<ContractView> contracts(@PathVariable UUID id) {
        return contracts.ofVehicle(id);
    }

    @PostMapping("/{id}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a lease contract", description = "Schedules the end-of-contract deadline")
    public ContractView createContract(@PathVariable UUID id, @Valid @RequestBody CreateContract command) {
        return contracts.create(id, command);
    }

    @PostMapping("/{id}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Declare a claim", description = "Without a driver, the current driver of the vehicle is used")
    public IncidentView declareIncident(@PathVariable UUID id, @Valid @RequestBody DeclareIncident command) {
        return incidents.declare(id, command);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import vehicles from a CSV file", description = "Semicolon separated, UTF-8. Valid lines are "
            + "created, the report lists the rejected ones with their errors.")
    public ImportReport importCsv(@RequestParam UUID companyId, @RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessRuleException("CSV_EMPTY", "The file is empty");
        }
        try {
            return imports.importCsv(companyId, file.getInputStream());
        } catch (IOException e) {
            throw new BusinessRuleException("CSV_UNREADABLE", "The file could not be read");
        }
    }
}
