package com.fleetpilot.vehicle.api.rest;

import java.net.URI;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.AssignmentService;
import com.fleetpilot.vehicle.application.DriverService;
import com.fleetpilot.vehicle.application.command.DriverCommands.CreateDriver;
import com.fleetpilot.vehicle.application.command.DriverCommands.EndAssignment;
import com.fleetpilot.vehicle.application.command.DriverCommands.UpdateDriver;
import com.fleetpilot.vehicle.application.view.Views.AssignmentView;
import com.fleetpilot.vehicle.application.view.Views.DriverDetail;
import com.fleetpilot.vehicle.application.view.Views.DriverSummary;
import com.fleetpilot.vehicle.domain.repository.SearchCriteria.DriverCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Drivers", description = "Drivers and vehicle assignments")
public class DriverController {

    private final DriverService drivers;
    private final AssignmentService assignments;

    public DriverController(DriverService drivers, AssignmentService assignments) {
        this.drivers = drivers;
        this.assignments = assignments;
    }

    @GetMapping("/drivers")
    @Operation(summary = "Search drivers")
    public PageResponse<DriverSummary> search(
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID siteId,
            @RequestParam(required = false) Boolean active,
            @Parameter(description = "Name, e-mail or employee number") @RequestParam(required = false) String q,
            @Parameter(description = "With (true) or without (false) a current vehicle")
            @RequestParam(required = false) Boolean assigned,
            @ParameterObject @PageableDefault(size = 20, sort = {"lastName", "firstName"}) Pageable pageable) {
        return PageResponse.of(drivers.search(new DriverCriteria(companyId, siteId, active, q, assigned), pageable));
    }

    @GetMapping("/drivers/{id}")
    @Operation(summary = "Driver file", description = "With assignments history and claims")
    public DriverDetail get(@PathVariable UUID id) {
        return drivers.get(id);
    }

    @PostMapping("/drivers")
    @Operation(summary = "Register a driver")
    public ResponseEntity<DriverDetail> create(@Valid @RequestBody CreateDriver command) {
        DriverDetail created = drivers.create(command);
        return ResponseEntity.created(URI.create("/api/v1/drivers/" + created.driver().id())).body(created);
    }

    @PutMapping("/drivers/{id}")
    @Operation(summary = "Update a driver", description = "Optimistic locking: send the version you read")
    public DriverDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateDriver command) {
        return drivers.update(id, command);
    }

    @PostMapping("/assignments/{id}/end")
    @Operation(summary = "The driver returns the vehicle")
    public AssignmentView endAssignment(@PathVariable UUID id, @Valid @RequestBody EndAssignment command) {
        return assignments.end(id, command);
    }
}
