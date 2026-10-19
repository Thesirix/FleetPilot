package com.fleetpilot.quote.api.rest;

import com.fleetpilot.common.security.Roles;
import com.fleetpilot.quote.application.AnalysisRulesService;
import com.fleetpilot.quote.application.AnalysisRulesService.RulesView;
import com.fleetpilot.quote.application.AnalysisRulesService.SaveRules;
import com.fleetpilot.quote.application.TariffService;
import com.fleetpilot.quote.application.command.QuoteCommands.UpdateRate;
import com.fleetpilot.quote.application.view.QuoteViews.RateView;
import com.fleetpilot.quote.application.view.QuoteViews.TariffView;
import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.Region;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tariff")
@Tag(name = "Reference tariff", description = "Part prices, standard labour times and negotiated hourly rates")
public class TariffController {

    private final TariffService tariff;
    private final AnalysisRulesService rules;

    public TariffController(TariffService tariff, AnalysisRulesService rules) {
        this.tariff = tariff;
        this.rules = rules;
    }

    @GetMapping("/rules")
    @PreAuthorize(Roles.ANY_MANAGER_OR_GARAGE)
    @Operation(summary = "Thresholds of the analysis and approval limits", description = "Shown to the garages too")
    public RulesView rules() {
        return rules.current();
    }

    @PutMapping("/rules")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Change the thresholds of the analysis and the approval limits",
            description = "Applies to the quotes analysed from now on")
    public RulesView changeRules(@Valid @RequestBody SaveRules command) {
        return rules.change(command);
    }

    @GetMapping
    @PreAuthorize(Roles.ANY_MANAGER_OR_GARAGE)
    @Operation(summary = "The whole reference tariff", description = "Garages see it too: the rules are transparent")
    public TariffView get() {
        return tariff.tariff();
    }

    @PutMapping("/rates/{region}/{category}")
    @PreAuthorize(Roles.ADMIN_ONLY)
    @Operation(summary = "Update a negotiated hourly rate", description = "New quotes are analysed with the new rate")
    public RateView updateRate(@PathVariable Region region, @PathVariable LabourCategory category,
            @Valid @RequestBody UpdateRate command) {
        return tariff.updateRate(region, category, command);
    }
}
