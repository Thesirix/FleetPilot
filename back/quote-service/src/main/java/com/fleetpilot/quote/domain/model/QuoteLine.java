package com.fleetpilot.quote.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import com.fleetpilot.quote.domain.analysis.QuoteAnalysis.LineAnalysis;
import com.fleetpilot.quote.domain.analysis.QuoteLineInput;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One line of a quote, with the result of its comparison to the reference tariff. */
@Entity
@Table(name = "quote_line")
public class QuoteLine {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false, updatable = false)
    private Quote quote;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LineKind kind;

    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_quantity", precision = 8, scale = 2)
    private BigDecimal referenceQuantity;

    @Column(name = "reference_unit_price", precision = 10, scale = 2)
    private BigDecimal referenceUnitPrice;

    @Column(name = "reference_amount", precision = 12, scale = 2)
    private BigDecimal referenceAmount;

    @Enumerated(EnumType.STRING)
    private Finding finding;

    @Column(name = "finding_detail")
    private String findingDetail;

    protected QuoteLine() {
    }

    QuoteLine(UUID id, Quote quote, int position, QuoteLineInput input) {
        this.id = id;
        this.quote = quote;
        this.position = position;
        this.kind = input.kind();
        this.code = input.code();
        this.label = input.label().trim();
        this.quantity = input.quantity();
        this.unitPrice = input.unitPrice();
        this.amount = input.quantity().multiply(input.unitPrice()).setScale(2, RoundingMode.HALF_UP);
    }

    void apply(LineAnalysis analysis) {
        this.amount = analysis.amount();
        this.referenceQuantity = analysis.referenceQuantity();
        this.referenceUnitPrice = analysis.referenceUnitPrice();
        this.referenceAmount = analysis.referenceAmount();
        this.finding = analysis.finding();
        this.findingDetail = analysis.detail();
    }

    public QuoteLineInput toInput() {
        return new QuoteLineInput(kind, code, label, quantity, unitPrice);
    }

    /** What this line costs above the reference tariff (zero for a compliant line). */
    public BigDecimal overcharge() {
        if (finding == null || !finding.isOvercharge() || referenceAmount == null) {
            return BigDecimal.ZERO;
        }
        return amount.subtract(referenceAmount).max(BigDecimal.ZERO);
    }

    public UUID getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public LineKind getKind() {
        return kind;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getReferenceQuantity() {
        return referenceQuantity;
    }

    public BigDecimal getReferenceUnitPrice() {
        return referenceUnitPrice;
    }

    public BigDecimal getReferenceAmount() {
        return referenceAmount;
    }

    public Finding getFinding() {
        return finding;
    }

    public String getFindingDetail() {
        return findingDetail;
    }
}
