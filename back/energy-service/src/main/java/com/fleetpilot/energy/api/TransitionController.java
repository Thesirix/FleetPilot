package com.fleetpilot.energy.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.common.dto.PageResponse;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.energy.application.EnergyViews.AssessmentView;
import com.fleetpilot.energy.application.EnergyViews.EvModelView;
import com.fleetpilot.energy.application.EnergyViews.Plan;
import com.fleetpilot.energy.application.EnergyViews.Summary;
import com.fleetpilot.energy.application.EnergyViews.ZoneView;
import com.fleetpilot.energy.application.TransitionService;
import com.fleetpilot.energy.domain.Transition.Eligibility;
import com.fleetpilot.energy.domain.Transition.EvModel;
import com.fleetpilot.energy.domain.Transition.ZfeRule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/energy")
@Validated
@PreAuthorize(Roles.ANY_MANAGER)
@Tag(name = "Energy transition", description = "Electric eligibility, cost, CO2, low emission zones, greening plan")
public class TransitionController {

    private final TransitionService transition;

    public TransitionController(TransitionService transition) {
        this.transition = transition;
    }

    @GetMapping("/summary")
    @Operation(summary = "The fleet at a glance", description = "Vehicles by eligibility, electric share today and "
            + "potential, annual saving, CO2 avoidable, exposure to the low emission zones")
    public Summary summary(@RequestParam(required = false) UUID companyId) {
        return transition.summary(companyId);
    }

    @GetMapping("/assessments")
    @Operation(summary = "The assessment of each vehicle", description = "sort: annualSaving (default), renewalDate, "
            + "annualKm, registration")
    public PageResponse<AssessmentView> assessments(@RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) Eligibility status, @RequestParam(required = false) String category,
            @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return PageResponse.of(transition.search(companyId, status, category, q, sort, pageable));
    }

    @GetMapping("/assessments/{vehicleId}")
    @Operation(summary = "The assessment of a vehicle")
    public AssessmentView assessment(@PathVariable UUID vehicleId) {
        return transition.vehicle(vehicleId);
    }

    @GetMapping("/plan")
    @Operation(summary = "The greening plan", description = "Quarter by quarter until the end of 2028, and the share "
            + "of low emission vehicles among the renewals of each year against the quota of the loi LOM")
    public Plan plan(@RequestParam(required = false) UUID companyId) {
        return transition.plan(companyId);
    }

    @GetMapping("/catalog")
    @Operation(summary = "The electric models proposed", description = "With their realistic range (70 % of WLTP)")
    public List<EvModelView> catalog() {
        return transition.catalog();
    }

    @GetMapping("/zones")
    @Operation(summary = "The low emission zones", description = "Highest Crit'Air sticker allowed per city")
    public List<ZoneView> zones() {
        return transition.zones();
    }

    /** An electric model of the catalogue, as the administration edits it. */
    public record SaveModel(
            @NotBlank @Size(max = 40) String make,
            @NotBlank @Size(max = 80) String model,
            @NotBlank @Pattern(regexp = "CAR|VAN|TRUCK") String category,
            @NotBlank @Pattern(regexp = "CITY|COMPACT|LARGE|SMALL|MEDIUM|TRUCK") String segment,
            @NotNull @DecimalMin("10") @DecimalMax("1000") BigDecimal batteryKwh,
            @NotNull @Min(50) @Max(1500) Integer wltpRangeKm,
            @NotNull @DecimalMin("5") @DecimalMax("200") BigDecimal consumptionKwh100km,
            @NotNull @Min(7) @Max(1000) Integer fastChargeKw,
            @NotNull @DecimalMin("50") @DecimalMax("20000") BigDecimal monthlyRent) {
    }

    @PutMapping("/catalog/{code}")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Add or update an electric model", description = "The fleet is assessed again right away")
    public Map<String, Integer> saveModel(@PathVariable @Pattern(regexp = "[A-Z0-9-]{2,20}") String code,
            @Valid @RequestBody SaveModel m) {
        return Map.of("vehicles", transition.saveModel(new EvModel(code, m.make().trim(), m.model().trim(), m.category(),
                m.segment(), m.batteryKwh(), m.wltpRangeKm(), m.consumptionKwh100km(), m.fastChargeKw(),
                m.monthlyRent())));
    }

    @DeleteMapping("/catalog/{code}")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Remove an electric model from the catalogue")
    public Map<String, Integer> deleteModel(@PathVariable String code) {
        return Map.of("vehicles", transition.deleteModel(code));
    }

    /** A low emission zone, as the administration edits it. */
    public record SaveZone(
            @NotBlank @Size(max = 120) String zone,
            @NotNull @Min(1) @Max(5) Integer maxCritAir,
            @NotNull LocalDate effectiveFrom,
            @NotBlank @Pattern(regexp = "IN_FORCE|ANNOUNCED") String status) {
    }

    @PutMapping("/zones/{city}")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Add or update the low emission zone of a city", description = "The fleet is assessed again")
    public Map<String, Integer> saveZone(@PathVariable @Size(min = 2, max = 80) String city,
            @Valid @RequestBody SaveZone z) {
        return Map.of("vehicles", transition.saveZone(new ZfeRule(city.trim(), z.zone().trim(), z.maxCritAir(),
                z.effectiveFrom(), z.status())));
    }

    @DeleteMapping("/zones/{city}")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Remove the low emission zone of a city")
    public Map<String, Integer> deleteZone(@PathVariable String city) {
        return Map.of("vehicles", transition.deleteZone(city));
    }

    @PostMapping("/assessments/refresh")
    @PreAuthorize(Roles.ANY_STAFF)
    @Operation(summary = "Assess the fleet again now", description = "Done every night otherwise")
    public Map<String, Integer> refresh() {
        return Map.of("vehicles", transition.refresh());
    }
}
