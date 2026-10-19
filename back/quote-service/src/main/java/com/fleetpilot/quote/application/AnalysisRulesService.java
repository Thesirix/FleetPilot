package com.fleetpilot.quote.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.quote.domain.analysis.QuoteAnalyzer.Policy;
import com.fleetpilot.quote.domain.model.AnalysisRules;
import com.fleetpilot.quote.domain.repository.AnalysisRulesRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The rules of the quote analysis: read by every analysis and by the tariff page, changed by the administrators. */
@Service
@Transactional
public class AnalysisRulesService {

    @Schema(description = "Thresholds of the analysis (shares between 0 and 1) and approval limits in euros excl. VAT")
    public record RulesView(BigDecimal partTolerance, BigDecimal timeTolerance, BigDecimal rateTolerance,
            BigDecimal suppliesShare, BigDecimal suppliesMinimum, BigDecimal negotiationShare,
            BigDecimal negotiationMinimum, BigDecimal autoApprovalLimit, BigDecimal managerApprovalLimit,
            Instant updatedAt, String updatedBy, long version) {

        static RulesView of(AnalysisRules rules) {
            Policy p = rules.policy();
            return new RulesView(p.partTolerance(), p.timeTolerance(), p.rateTolerance(), p.suppliesShare(),
                    p.suppliesMinimum(), p.negotiationShare(), p.negotiationMinimum(), rules.getAutoApprovalLimit(),
                    rules.getManagerApprovalLimit(), rules.getUpdatedAt(), rules.getUpdatedBy(), rules.getVersion());
        }
    }

    public record SaveRules(
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal partTolerance,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal timeTolerance,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal rateTolerance,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal suppliesShare,
            @NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal suppliesMinimum,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal negotiationShare,
            @NotNull @DecimalMin("0") @DecimalMax("10000") BigDecimal negotiationMinimum,
            @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal autoApprovalLimit,
            @NotNull @DecimalMin("1") @DecimalMax("1000000") BigDecimal managerApprovalLimit,
            @NotNull Long version) {
    }

    private final AnalysisRulesRepository rules;
    private final Clock clock;

    public AnalysisRulesService(AnalysisRulesRepository rules, Clock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RulesView current() {
        return RulesView.of(load());
    }

    /** The rules applied to a quote analysed now. */
    @Transactional(readOnly = true)
    public AnalysisRules forAnalysis() {
        return load();
    }

    public RulesView change(SaveRules cmd) {
        AnalysisRules current = load();
        if (cmd.version() != current.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(AnalysisRules.class, AnalysisRules.ID);
        }
        if (cmd.autoApprovalLimit().compareTo(cmd.managerApprovalLimit()) >= 0) {
            throw new BusinessRuleException("AUTO_APPROVAL_ABOVE_LIMIT",
                    "The automatic approval limit must stay below the approval limit of a fleet manager");
        }
        current.change(new Policy(cmd.partTolerance(), cmd.timeTolerance(), cmd.rateTolerance(), cmd.suppliesShare(),
                        cmd.suppliesMinimum(), cmd.negotiationShare(), cmd.negotiationMinimum()),
                cmd.autoApprovalLimit(), cmd.managerApprovalLimit(), author(), clock.instant());
        rules.saveAndFlush(current);
        return RulesView.of(current);
    }

    private AnalysisRules load() {
        return rules.findById(AnalysisRules.ID).orElseThrow(() -> new IllegalStateException("No analysis rules"));
    }

    private static String author() {
        return CurrentUser.token()
                .map(token -> {
                    String name = token.getToken().getClaimAsString("name");
                    return name != null && !name.isBlank() ? name : CurrentUser.username().orElse(token.getName());
                })
                .orElse("FleetPilot");
    }
}
