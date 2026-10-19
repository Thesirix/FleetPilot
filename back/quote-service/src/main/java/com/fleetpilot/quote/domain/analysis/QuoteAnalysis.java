package com.fleetpilot.quote.domain.analysis;

import java.math.BigDecimal;
import java.util.List;

import com.fleetpilot.quote.domain.model.Finding;

/**
 * Result of the comparison of a quote with the reference tariff.
 *
 * @param lines           analysis of each line, in the order of the quote
 * @param amount          total quoted, excluding tax
 * @param referenceAmount what the same work costs at the reference tariff
 * @param overcharge      sum of the excess on the flagged lines: what the negotiation can save
 * @param verdict         what happens to the quote next
 */
public record QuoteAnalysis(List<LineAnalysis> lines, BigDecimal amount, BigDecimal referenceAmount,
        BigDecimal overcharge, Verdict verdict) {

    /** What the analysis recommends. */
    public enum Verdict {
        /** Every line matches the tariff. */
        COMPLIANT,
        /** No overcharge, but items absent from the tariff must be checked by a manager. */
        TO_CHECK,
        /** Overcharges large enough to negotiate. */
        TO_NEGOTIATE
    }

    /**
     * Analysis of one line.
     *
     * @param amount             quantity x unit price, as quoted
     * @param referenceQuantity  standard time for labour, quoted quantity otherwise
     * @param referenceUnitPrice reference price or negotiated rate
     * @param referenceAmount    fair amount for the line
     * @param finding            what is wrong, null when the line is compliant
     * @param detail             short technical explanation of the finding
     */
    public record LineAnalysis(BigDecimal amount, BigDecimal referenceQuantity, BigDecimal referenceUnitPrice,
            BigDecimal referenceAmount, Finding finding, String detail) {

        public boolean flagged() {
            return finding != null;
        }
    }

    public QuoteAnalysis {
        lines = List.copyOf(lines);
    }

    public long findingCount() {
        return lines.stream().filter(LineAnalysis::flagged).count();
    }
}
