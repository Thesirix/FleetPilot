package com.fleetpilot.vehicle.api.rest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.security.Roles;
import com.fleetpilot.vehicle.application.FleetStatsQuery;
import com.fleetpilot.vehicle.application.FleetStatsQuery.FleetSummary;
import com.fleetpilot.vehicle.application.FleetSweepService;
import com.fleetpilot.vehicle.application.FleetSweepService.SweepReport;
import com.fleetpilot.vehicle.application.ReferenceDataService;
import com.fleetpilot.vehicle.application.TenantScope;
import com.fleetpilot.vehicle.application.view.Views.CompanyView;
import com.fleetpilot.vehicle.application.view.Views.CostCenterView;
import com.fleetpilot.vehicle.application.view.Views.SiteView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Companies", description = "Client companies, their sites and cost centers, fleet indicators")
public class CompanyController {

    private final ReferenceDataService referenceData;
    private final FleetStatsQuery stats;
    private final FleetSweepService sweep;
    private final TenantScope tenant;
    private final Clock clock;

    public CompanyController(ReferenceDataService referenceData, FleetStatsQuery stats, FleetSweepService sweep,
            TenantScope tenant, Clock clock) {
        this.referenceData = referenceData;
        this.stats = stats;
        this.sweep = sweep;
        this.tenant = tenant;
        this.clock = clock;
    }

    @GetMapping("/companies")
    @Operation(summary = "Client companies visible to the caller")
    public List<CompanyView> companies() {
        return referenceData.companies();
    }

    @GetMapping("/companies/{id}")
    @Operation(summary = "Client company")
    public CompanyView company(@PathVariable UUID id) {
        return referenceData.company(id);
    }

    @GetMapping("/companies/{id}/sites")
    @Operation(summary = "Sites (depots, offices) of a company")
    public List<SiteView> sites(@PathVariable UUID id) {
        return referenceData.sites(id);
    }

    @GetMapping("/companies/{id}/cost-centers")
    @Operation(summary = "Cost centers of a company")
    public List<CostCenterView> costCenters(@PathVariable UUID id) {
        return referenceData.costCenters(id);
    }

    @GetMapping("/fleet/summary")
    @Operation(summary = "Fleet indicators", description = "For one company, or for every visible company when omitted")
    public FleetSummary summary(@RequestParam(required = false) UUID companyId) {
        return stats.summary(tenant.companyFilter(companyId), LocalDate.now(clock));
    }

    @PostMapping("/fleet/sweep")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Run the daily sweep now", description = "Sends the due reminders and mileage overrun "
            + "warnings that have not been sent yet. It normally runs every morning at 6:00.")
    public SweepReport runSweep() {
        return sweep.run();
    }
}
