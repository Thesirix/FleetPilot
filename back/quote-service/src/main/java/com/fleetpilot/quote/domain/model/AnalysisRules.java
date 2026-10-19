package com.fleetpilot.quote.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.fleetpilot.quote.domain.analysis.QuoteAnalyzer.Policy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * The thresholds of the quote analysis and the approval limits: one row, set by the platform administrators. A change
 * applies to the quotes analysed from then on; the quotes already analysed keep their verdict.
 */
@Entity
@Table(name = "analysis_rules")
public class AnalysisRules {

    public static final short ID = 1;

    @Id
    private short id;

    @Column(name = "part_tolerance", nullable = false)
    private BigDecimal partTolerance;

    @Column(name = "time_tolerance", nullable = false)
    private BigDecimal timeTolerance;

    @Column(name = "rate_tolerance", nullable = false)
    private BigDecimal rateTolerance;

    @Column(name = "supplies_share", nullable = false)
    private BigDecimal suppliesShare;

    @Column(name = "supplies_minimum", nullable = false)
    private BigDecimal suppliesMinimum;

    @Column(name = "negotiation_share", nullable = false)
    private BigDecimal negotiationShare;

    @Column(name = "negotiation_minimum", nullable = false)
    private BigDecimal negotiationMinimum;

    @Column(name = "auto_approval_limit", nullable = false)
    private BigDecimal autoApprovalLimit;

    @Column(name = "manager_approval_limit", nullable = false)
    private BigDecimal managerApprovalLimit;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Version
    private long version;

    protected AnalysisRules() {
    }

    /** The analysis policy of these rules. */
    public Policy policy() {
        return new Policy(partTolerance, timeTolerance, rateTolerance, suppliesShare, suppliesMinimum, negotiationShare,
                negotiationMinimum);
    }

    public void change(Policy policy, BigDecimal autoApprovalLimit, BigDecimal managerApprovalLimit, String author,
            Instant now) {
        this.partTolerance = policy.partTolerance();
        this.timeTolerance = policy.timeTolerance();
        this.rateTolerance = policy.rateTolerance();
        this.suppliesShare = policy.suppliesShare();
        this.suppliesMinimum = policy.suppliesMinimum();
        this.negotiationShare = policy.negotiationShare();
        this.negotiationMinimum = policy.negotiationMinimum();
        this.autoApprovalLimit = autoApprovalLimit;
        this.managerApprovalLimit = managerApprovalLimit;
        this.updatedBy = author;
        this.updatedAt = now;
    }

    public BigDecimal getAutoApprovalLimit() {
        return autoApprovalLimit;
    }

    public BigDecimal getManagerApprovalLimit() {
        return managerApprovalLimit;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public long getVersion() {
        return version;
    }
}
