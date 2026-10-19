package com.fleetpilot.vehicle.api.rest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.AuditQuery;
import com.fleetpilot.vehicle.application.CompanyAdminService;
import com.fleetpilot.vehicle.application.command.AdminCommands.SaveCompany;
import com.fleetpilot.vehicle.application.command.AdminCommands.SaveCostCenter;
import com.fleetpilot.vehicle.application.command.AdminCommands.SaveSite;
import com.fleetpilot.vehicle.application.view.Views.AuditView;
import com.fleetpilot.vehicle.application.view.Views.CompanyAdminView;
import com.fleetpilot.vehicle.application.view.Views.CompanyView;
import com.fleetpilot.vehicle.application.view.Views.CostCenterAdminView;
import com.fleetpilot.vehicle.application.view.Views.CostCenterView;
import com.fleetpilot.vehicle.application.view.Views.SiteAdminView;
import com.fleetpilot.vehicle.application.view.Views.SiteView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
@PreAuthorize(Roles.ANY_STAFF)
@Tag(name = "Administration", description = "Client companies, sites, cost centers and the activity journal (FleetPilot staff)")
public class AdminController {

    private final CompanyAdminService companies;
    private final AuditQuery audit;

    public AdminController(CompanyAdminService companies, AuditQuery audit) {
        this.companies = companies;
        this.audit = audit;
    }

    @GetMapping("/companies")
    @Operation(summary = "Client companies with the size of their fleet")
    public List<CompanyAdminView> companies() {
        return companies.companies();
    }

    @GetMapping("/companies/{id}")
    @Operation(summary = "Client company with the size of its fleet")
    public CompanyAdminView company(@PathVariable UUID id) {
        return companies.company(id);
    }

    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a client company")
    public CompanyView createCompany(@Valid @RequestBody SaveCompany cmd) {
        return companies.create(cmd);
    }

    @PutMapping("/companies/{id}")
    @Operation(summary = "Update a client company")
    public CompanyView updateCompany(@PathVariable UUID id, @Valid @RequestBody SaveCompany cmd) {
        return companies.update(id, cmd);
    }

    @PostMapping("/companies/{id}/activation")
    @Operation(summary = "Activate or deactivate a client company")
    public CompanyView activation(@PathVariable UUID id, @RequestParam boolean active) {
        return companies.setActive(id, active);
    }

    @GetMapping("/companies/{id}/sites")
    @Operation(summary = "Sites of a company, with their vehicles and drivers")
    public List<SiteAdminView> sites(@PathVariable UUID id) {
        return companies.sites(id);
    }

    @PostMapping("/companies/{id}/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a site")
    public SiteView createSite(@PathVariable UUID id, @Valid @RequestBody SaveSite cmd) {
        return companies.createSite(id, cmd);
    }

    @PutMapping("/sites/{id}")
    @Operation(summary = "Update a site")
    public SiteView updateSite(@PathVariable UUID id, @Valid @RequestBody SaveSite cmd) {
        return companies.updateSite(id, cmd);
    }

    @DeleteMapping("/sites/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a site without vehicle nor driver")
    public void deleteSite(@PathVariable UUID id) {
        companies.deleteSite(id);
    }

    @GetMapping("/companies/{id}/cost-centers")
    @Operation(summary = "Cost centers of a company, with their vehicles")
    public List<CostCenterAdminView> costCenters(@PathVariable UUID id) {
        return companies.costCenters(id);
    }

    @PostMapping("/companies/{id}/cost-centers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a cost center")
    public CostCenterView createCostCenter(@PathVariable UUID id, @Valid @RequestBody SaveCostCenter cmd) {
        return companies.createCostCenter(id, cmd);
    }

    @PutMapping("/cost-centers/{id}")
    @Operation(summary = "Update a cost center")
    public CostCenterView updateCostCenter(@PathVariable UUID id, @Valid @RequestBody SaveCostCenter cmd) {
        return companies.updateCostCenter(id, cmd);
    }

    @DeleteMapping("/cost-centers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a cost center no vehicle is charged to")
    public void deleteCostCenter(@PathVariable UUID id) {
        companies.deleteCostCenter(id);
    }

    @GetMapping("/audit")
    @PreAuthorize(Roles.ANY_MANAGER)
    @Operation(summary = "Activity journal", description = "Who did what, newest first. A client manager only sees "
            + "the actions on their company.")
    public PageResponse<AuditView> audit(
            @RequestParam(required = false) UUID companyId,
            @Parameter(description = "VEHICLE, DRIVER, CONTRACT, DEADLINE, INCIDENT, COMPANY, SITE, COST_CENTER, USER")
            @RequestParam(required = false) String targetType,
            @Parameter(description = "Part of the name of the author") @RequestParam(required = false) String actor,
            @RequestParam(required = false) Instant since,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(200) int size) {
        return PageResponse.of(audit.search(companyId, targetType, actor, since, PageRequest.of(page, size)));
    }
}
