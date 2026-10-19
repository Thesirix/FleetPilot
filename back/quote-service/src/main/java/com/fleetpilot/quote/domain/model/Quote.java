package com.fleetpilot.quote.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.quote.domain.analysis.QuoteAnalysis;
import com.fleetpilot.quote.domain.analysis.QuoteAnalysis.Verdict;
import com.fleetpilot.quote.domain.analysis.QuoteLineInput;
import com.fleetpilot.quote.domain.model.QuoteHistoryEntry.Action;
import com.fleetpilot.quote.domain.model.QuoteHistoryEntry.Party;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A garage quote and its whole life: analysis against the reference tariff, negotiation, decision, work and invoice.
 * All amounts are excluding tax.
 */
@Entity
@Table(name = "quote")
public class Quote {

    /** An invoice may exceed the accepted amount by 2 %, with a minimum of 10 EUR (rounding, small supplies). */
    static final BigDecimal INVOICE_TOLERANCE_SHARE = new BigDecimal("0.02");
    static final BigDecimal INVOICE_TOLERANCE_MINIMUM = new BigDecimal("10.00");

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garage_id", nullable = false, updatable = false)
    private Garage garage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private VehicleRef vehicle;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false)
    private String registration;

    @Column(name = "vehicle_category")
    private String vehicleCategory;

    @Column(name = "garage_reference")
    private String garageReference;

    private Integer mileage;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private QuoteChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "initial_amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal initialAmount;

    @Column(name = "reference_amount", precision = 12, scale = 2)
    private BigDecimal referenceAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal overcharge;

    @Column(name = "accepted_amount", precision = 12, scale = 2)
    private BigDecimal acceptedAmount;

    @Column(name = "finding_count", nullable = false)
    private int findingCount;

    @Column(name = "auto_approved", nullable = false)
    private boolean autoApproved;

    @Column(name = "counter_offer", precision = 12, scale = 2)
    private BigDecimal counterOffer;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "analysed_at")
    private Instant analysedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private String decidedBy;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "invoice_amount", precision = 12, scale = 2)
    private BigDecimal invoiceAmount;

    @Column(name = "invoiced_on")
    private LocalDate invoicedOn;

    @Column(name = "invoice_disputed", nullable = false)
    private boolean invoiceDisputed;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<QuoteLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("at")
    private List<QuoteHistoryEntry> history = new ArrayList<>();

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Quote() {
    }

    /** What the garage sends. */
    public record Submission(String registration, String garageReference, Integer mileage, String description,
            QuoteChannel channel, List<QuoteLineInput> lines) {

        public Submission {
            Objects.requireNonNull(registration, "registration");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(channel, "channel");
            if (lines == null || lines.isEmpty()) {
                throw new BusinessRuleException("QUOTE_WITHOUT_LINES", "A quote needs at least one line");
            }
            if (mileage != null && mileage < 0) {
                throw new BusinessRuleException("INVALID_MILEAGE", "The mileage cannot be negative");
            }
            lines = List.copyOf(lines);
        }
    }

    /** Receives a quote. Without an identified vehicle it stays RECEIVED until a manager links it. */
    public Quote(UUID id, String reference, Garage garage, VehicleRef vehicle, Submission submission, String author,
            Instant now) {
        this.id = Objects.requireNonNull(id);
        this.reference = Objects.requireNonNull(reference);
        this.garage = Objects.requireNonNull(garage);
        this.registration = submission.registration();
        this.garageReference = submission.garageReference();
        this.mileage = submission.mileage();
        this.description = submission.description().trim();
        this.channel = submission.channel();
        this.status = QuoteStatus.RECEIVED;
        this.revision = 1;
        this.submittedAt = now;
        replaceLines(submission.lines());
        this.initialAmount = this.amount;
        this.overcharge = BigDecimal.ZERO;
        record(now, Party.GARAGE, author, Action.SUBMITTED, amount, null);
        if (vehicle != null) {
            this.vehicle = vehicle;
            this.companyId = vehicle.getCompanyId();
            this.vehicleCategory = vehicle.getCategory();
        }
    }

    // ---------------------------------------------------------------- analysis

    /** Links the vehicle a manager identified from the registration written by the garage. */
    public void identifyVehicle(VehicleRef identified, String author, Instant now) {
        if (status != QuoteStatus.RECEIVED) {
            throw new BusinessRuleException("VEHICLE_ALREADY_IDENTIFIED", "The vehicle of this quote is already known");
        }
        this.vehicle = Objects.requireNonNull(identified);
        this.companyId = identified.getCompanyId();
        this.vehicleCategory = identified.getCategory();
        this.registration = identified.getRegistration();
        record(now, Party.MANAGER, author, Action.VEHICLE_IDENTIFIED, null, identified.getRegistration());
    }

    /**
     * Applies the comparison with the reference tariff. A compliant quote under the automatic approval limit is
     * accepted at once (straight-through processing); otherwise it waits for a manager.
     */
    public void applyAnalysis(QuoteAnalysis analysis, BigDecimal autoApprovalLimit, Instant now) {
        if (vehicle == null) {
            throw new BusinessRuleException("VEHICLE_NOT_IDENTIFIED", "The vehicle must be identified before the analysis");
        }
        if (!status.isOpen()) {
            throw new BusinessRuleException("QUOTE_CLOSED", "Quote " + reference + " is " + status);
        }
        if (analysis.lines().size() != lines.size()) {
            throw new IllegalArgumentException("The analysis does not match the lines of the quote");
        }
        for (int i = 0; i < lines.size(); i++) {
            lines.get(i).apply(analysis.lines().get(i));
        }
        this.amount = analysis.amount();
        this.referenceAmount = analysis.referenceAmount();
        this.overcharge = analysis.overcharge();
        this.findingCount = (int) analysis.findingCount();
        this.analysedAt = now;
        record(now, Party.SYSTEM, "FleetPilot", Action.ANALYSED, overcharge, analysis.verdict().name());
        if (analysis.verdict() == Verdict.COMPLIANT && amount.compareTo(autoApprovalLimit) <= 0) {
            this.status = QuoteStatus.ACCEPTED;
            this.acceptedAmount = amount;
            this.autoApproved = true;
            this.decidedAt = now;
            this.decidedBy = "FleetPilot";
            record(now, Party.SYSTEM, "FleetPilot", Action.AUTO_APPROVED, amount, null);
        } else {
            this.status = analysis.verdict() == Verdict.TO_NEGOTIATE ? QuoteStatus.TO_NEGOTIATE : QuoteStatus.IN_REVIEW;
        }
    }

    // ---------------------------------------------------------------- negotiation

    /** The manager proposes a lower amount to the garage. */
    public void proposeCounterOffer(BigDecimal proposed, String message, String author, Instant now) {
        requireAnalysedAndOpen();
        if (proposed == null || proposed.signum() <= 0 || proposed.compareTo(amount) >= 0) {
            throw new BusinessRuleException("INVALID_COUNTER_OFFER",
                    "A counter-offer must be positive and lower than the quoted amount");
        }
        this.counterOffer = proposed.setScale(2, RoundingMode.HALF_UP);
        this.status = QuoteStatus.TO_NEGOTIATE;
        record(now, Party.MANAGER, author, Action.COUNTER_OFFER, counterOffer, message);
    }

    /** The garage agrees to the counter-offer: the quote is accepted at that amount. */
    public void acceptCounterOffer(String author, Instant now) {
        requirePendingCounterOffer();
        BigDecimal agreed = counterOffer;
        record(now, Party.GARAGE, author, Action.COUNTER_ACCEPTED, agreed, null);
        decide(QuoteStatus.ACCEPTED, agreed, author, null, now);
        this.counterOffer = null;
    }

    /** The garage refuses the counter-offer; the manager decides what to do next. */
    public void refuseCounterOffer(String message, String author, Instant now) {
        requirePendingCounterOffer();
        record(now, Party.GARAGE, author, Action.COUNTER_REFUSED, counterOffer, message);
        this.counterOffer = null;
    }

    /** The garage sends a new version of its quote; it is analysed again. */
    /**
     * First step of a new version: the old lines go away, so that the database deletes them before the new lines,
     * which take the same positions, are inserted.
     */
    public void discardLinesForRevision() {
        if (!status.isOpen()) {
            throw new BusinessRuleException("QUOTE_CLOSED", "Quote " + reference + " is " + status);
        }
        lines.clear();
    }

    public void revise(List<QuoteLineInput> newLines, String message, String author, Instant now) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("QUOTE_CLOSED", "Quote " + reference + " is " + status);
        }
        if (newLines == null || newLines.isEmpty()) {
            throw new BusinessRuleException("QUOTE_WITHOUT_LINES", "A quote needs at least one line");
        }
        replaceLines(newLines);
        this.revision++;
        this.counterOffer = null;
        record(now, Party.GARAGE, author, Action.REVISED, amount, message);
    }

    // ---------------------------------------------------------------- decision, work, invoice

    /** The manager accepts the quote as it stands. Accepting despite overcharges needs a justification. */
    public void accept(String comment, String author, Instant now) {
        requireAnalysedAndOpen();
        if (overcharge.signum() > 0 && (comment == null || comment.isBlank())) {
            throw new BusinessRuleException("JUSTIFICATION_REQUIRED",
                    "Accepting a quote with overcharges requires a justification");
        }
        record(now, Party.MANAGER, author, Action.ACCEPTED, amount, comment);
        decide(QuoteStatus.ACCEPTED, amount, author, comment, now);
        this.counterOffer = null;
    }

    public void reject(String reason, String author, Instant now) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("QUOTE_CLOSED", "Quote " + reference + " is " + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("REASON_REQUIRED", "Rejecting a quote requires a reason");
        }
        record(now, Party.MANAGER, author, Action.REJECTED, null, reason);
        decide(QuoteStatus.REJECTED, null, author, reason, now);
        this.counterOffer = null;
    }

    /** The garage reports that the work is done. */
    public void complete(LocalDate on, String author, Instant now) {
        move(QuoteStatus.COMPLETED);
        this.completedOn = Objects.requireNonNull(on);
        record(now, Party.GARAGE, author, Action.COMPLETED, null, null);
    }

    /**
     * Records the invoice and reconciles it with the accepted amount: an invoice above the accepted amount beyond the
     * tolerance is disputed. A garage may invoice right after the work, so an accepted quote is completed on the way.
     */
    public void invoice(String number, BigDecimal invoiced, LocalDate on, String author, Instant now) {
        if (status == QuoteStatus.ACCEPTED) {
            complete(on, author, now);
        }
        if (invoiced == null || invoiced.signum() <= 0) {
            throw new BusinessRuleException("INVALID_INVOICE_AMOUNT", "The invoice amount must be positive");
        }
        move(QuoteStatus.INVOICED);
        this.invoiceNumber = Objects.requireNonNull(number).trim();
        this.invoiceAmount = invoiced.setScale(2, RoundingMode.HALF_UP);
        this.invoicedOn = on;
        BigDecimal tolerance = acceptedAmount.multiply(INVOICE_TOLERANCE_SHARE).max(INVOICE_TOLERANCE_MINIMUM);
        this.invoiceDisputed = invoiceVariance().compareTo(tolerance) > 0;
        record(now, Party.GARAGE, author, Action.INVOICED, invoiceAmount, number);
    }

    // ---------------------------------------------------------------- derived values

    /** Saving obtained: what the garage first asked minus what was accepted. Null while not accepted. */
    public BigDecimal savings() {
        return acceptedAmount == null ? null : initialAmount.subtract(acceptedAmount).max(BigDecimal.ZERO);
    }

    /** Invoice minus accepted amount, null without invoice. */
    public BigDecimal invoiceVariance() {
        return invoiceAmount == null || acceptedAmount == null ? null : invoiceAmount.subtract(acceptedAmount);
    }

    public List<QuoteLineInput> lineInputs() {
        return lines.stream().map(QuoteLine::toInput).toList();
    }

    public TariffClass tariffClass() {
        return TariffClass.ofVehicleCategory(vehicleCategory);
    }

    // ---------------------------------------------------------------- internals

    private void replaceLines(List<QuoteLineInput> inputs) {
        lines.clear();
        int position = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (QuoteLineInput input : inputs) {
            QuoteLine line = new QuoteLine(UUID.randomUUID(), this, position++, input);
            lines.add(line);
            total = total.add(line.getAmount());
        }
        this.amount = total;
        this.referenceAmount = null;
        this.overcharge = BigDecimal.ZERO;
        this.findingCount = 0;
    }

    private void decide(QuoteStatus target, BigDecimal accepted, String author, String comment, Instant now) {
        move(target);
        this.acceptedAmount = accepted;
        this.decidedAt = now;
        this.decidedBy = author;
        this.decisionComment = comment;
    }

    private void move(QuoteStatus target) {
        if (!status.canMoveTo(target)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Quote " + reference + " cannot go from " + status + " to " + target);
        }
        this.status = target;
    }

    private void requireAnalysedAndOpen() {
        if (status == QuoteStatus.RECEIVED || analysedAt == null) {
            throw new BusinessRuleException("QUOTE_NOT_ANALYSED", "The quote must be analysed first");
        }
        if (!status.isOpen()) {
            throw new BusinessRuleException("QUOTE_CLOSED", "Quote " + reference + " is " + status);
        }
    }

    private void requirePendingCounterOffer() {
        if (counterOffer == null || !status.isOpen()) {
            throw new BusinessRuleException("NO_COUNTER_OFFER", "There is no counter-offer to answer");
        }
    }

    private void record(Instant at, Party party, String author, Action action, BigDecimal value, String message) {
        history.add(new QuoteHistoryEntry(this, at, party, author, action, value, message));
    }

    // ---------------------------------------------------------------- getters

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Garage getGarage() {
        return garage;
    }

    public VehicleRef getVehicle() {
        return vehicle;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getRegistration() {
        return registration;
    }

    public String getVehicleCategory() {
        return vehicleCategory;
    }

    public String getGarageReference() {
        return garageReference;
    }

    public Integer getMileage() {
        return mileage;
    }

    public String getDescription() {
        return description;
    }

    public QuoteChannel getChannel() {
        return channel;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public int getRevision() {
        return revision;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getInitialAmount() {
        return initialAmount;
    }

    public BigDecimal getReferenceAmount() {
        return referenceAmount;
    }

    public BigDecimal getOvercharge() {
        return overcharge;
    }

    public BigDecimal getAcceptedAmount() {
        return acceptedAmount;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public boolean isAutoApproved() {
        return autoApproved;
    }

    public BigDecimal getCounterOffer() {
        return counterOffer;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getAnalysedAt() {
        return analysedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public BigDecimal getInvoiceAmount() {
        return invoiceAmount;
    }

    public LocalDate getInvoicedOn() {
        return invoicedOn;
    }

    public boolean isInvoiceDisputed() {
        return invoiceDisputed;
    }

    public List<QuoteLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<QuoteHistoryEntry> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
