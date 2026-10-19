package com.fleetpilot.vehicle.api.rest;

import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.ContractService;
import com.fleetpilot.vehicle.application.DeadlineService;
import com.fleetpilot.vehicle.application.DeadlineService.Completion;
import com.fleetpilot.vehicle.application.IncidentService;
import com.fleetpilot.vehicle.application.command.FleetCommands.AssessIncident;
import com.fleetpilot.vehicle.application.command.FleetCommands.ChangeIncidentStatus;
import com.fleetpilot.vehicle.application.command.FleetCommands.CompleteDeadline;
import com.fleetpilot.vehicle.application.command.FleetCommands.EndContract;
import com.fleetpilot.vehicle.application.view.Views.ContractView;
import com.fleetpilot.vehicle.application.view.Views.DeadlineView;
import com.fleetpilot.vehicle.application.view.Views.IncidentView;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Incident;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.Urgency;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.ContractCriteria;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.DeadlineCriteria;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.IncidentCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Day-to-day follow-up of the fleet: lease contracts, deadlines and claims. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Follow-up", description = "Lease contracts, deadlines and claims")
public class FollowUpController {

    private final ContractService contracts;
    private final DeadlineService deadlines;
    private final IncidentService incidents;

    public FollowUpController(ContractService contracts, DeadlineService deadlines, IncidentService incidents) {
        this.contracts = contracts;
        this.deadlines = deadlines;
        this.incidents = incidents;
    }

    // ---------------------------------------------------------------- contracts

    @GetMapping("/contracts")
    @PreAuthorize(Roles.ANY_MANAGER_OR_SUPPLIER)
    @Operation(summary = "Search lease contracts", description = "With the projected mileage at the end of the contract. "
            + "A lessor (token with a lessor claim) only gets its own contracts")
    public PageResponse<ContractView> contracts(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String lessor,
            @RequestParam(required = false) LeaseContract.Status status,
            @Parameter(description = "Only contracts ending on or before this date")
            @RequestParam(required = false) LocalDate endingBefore,
            @ParameterObject @PageableDefault(size = 20, sort = "endDate") Pageable pageable) {
        return PageResponse.of(contracts.search(new ContractCriteria(companyId, lessor, status, endingBefore), pageable));
    }

    @GetMapping("/contracts/{id}")
    @PreAuthorize(Roles.ANY_MANAGER_OR_SUPPLIER)
    @Operation(summary = "Lease contract")
    public ContractView contract(@PathVariable UUID id) {
        return contracts.get(id);
    }

    @PostMapping("/contracts/{id}/end")
    @Operation(summary = "End a lease contract", description = "The vehicle was returned or bought back")
    public ContractView endContract(@PathVariable UUID id, @Valid @RequestBody EndContract command) {
        return contracts.end(id, command);
    }

    // ---------------------------------------------------------------- deadlines

    @GetMapping("/deadlines")
    @Operation(summary = "Open deadlines", description = "Filter by urgency to build the to-do list of the fleet manager")
    public PageResponse<DeadlineView> deadlines(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) DeadlineType type,
            @RequestParam(required = false) Urgency urgency,
            @RequestParam(required = false) LocalDate dueBefore,
            @ParameterObject @PageableDefault(size = 20, sort = "dueDate") Pageable pageable) {
        return PageResponse.of(deadlines.search(
                new DeadlineCriteria(companyId, vehicleId, type, urgency, dueBefore, null), pageable));
    }

    @PostMapping("/deadlines/{id}/complete")
    @Operation(summary = "Mark a deadline as done", description = "Schedules the next occurrence")
    public Completion completeDeadline(@PathVariable UUID id, @Valid @RequestBody CompleteDeadline command) {
        return deadlines.complete(id, command);
    }

    // ---------------------------------------------------------------- incidents

    @GetMapping("/incidents")
    @Operation(summary = "Search claims")
    public PageResponse<IncidentView> incidents(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) Incident.Status status,
            @RequestParam(required = false) Incident.Type type,
            @Parameter(description = "Only open (true) or closed (false) claims") @RequestParam(required = false) Boolean open,
            @ParameterObject @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.of(incidents.search(new IncidentCriteria(companyId, vehicleId, status, type, open), pageable));
    }

    @GetMapping("/incidents/{id}")
    @Operation(summary = "Claim")
    public IncidentView incident(@PathVariable UUID id) {
        return incidents.get(id);
    }

    @PutMapping("/incidents/{id}/assessment")
    @Operation(summary = "Assess a claim", description = "Responsibility and estimated cost")
    public IncidentView assess(@PathVariable UUID id, @Valid @RequestBody AssessIncident command) {
        return incidents.assess(id, command);
    }

    @PatchMapping("/incidents/{id}/status")
    @Operation(summary = "Move a claim forward")
    public IncidentView changeIncidentStatus(@PathVariable UUID id, @Valid @RequestBody ChangeIncidentStatus command) {
        return incidents.changeStatus(id, command);
    }
}
