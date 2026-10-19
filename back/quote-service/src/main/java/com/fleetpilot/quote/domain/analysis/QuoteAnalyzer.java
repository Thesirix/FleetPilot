package com.fleetpilot.quote.domain.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fleetpilot.quote.domain.analysis.QuoteAnalysis.LineAnalysis;
import com.fleetpilot.quote.domain.analysis.QuoteAnalysis.Verdict;
import com.fleetpilot.quote.domain.analysis.Tariff.PartPrice;
import com.fleetpilot.quote.domain.analysis.Tariff.StandardTime;
import com.fleetpilot.quote.domain.model.Finding;
import com.fleetpilot.quote.domain.model.LineKind;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.TariffClass;

/**
 * Compares a garage quote with the reference tariff, line by line. Pure business logic, without any framework.
 *
 * <ul>
 *   <li><b>Part</b>: unit price above the reference price by more than {@link Policy#partTolerance()}.</li>
 *   <li><b>Labour</b>: time above the standard time by more than {@link Policy#timeTolerance()}, or hourly rate above
 *       the negotiated rate of the region by more than {@link Policy#rateTolerance()}. The fair amount is the
 *       standard time at the negotiated rate.</li>
 *   <li><b>Duplicate</b>: the same part or operation billed twice; the second line is not due.</li>
 *   <li><b>Supplies</b>: consumables are allowed up to {@link Policy#suppliesShare()} of the parts and labour, with a
 *       minimum of {@link Policy#suppliesMinimum()}.</li>
 *   <li><b>Unknown item</b>: no code, or a code absent from the tariff. No saving is claimed, a manager checks it.</li>
 * </ul>
 *
 * The quote is worth negotiating when the overcharge exceeds {@link Policy#negotiationShare()} of the amount and at
 * least {@link Policy#negotiationMinimum()}.
 */
public class QuoteAnalyzer {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Thresholds of the analysis, agreed with the client companies. */
    public record Policy(BigDecimal partTolerance, BigDecimal timeTolerance, BigDecimal rateTolerance,
            BigDecimal suppliesShare, BigDecimal suppliesMinimum, BigDecimal negotiationShare,
            BigDecimal negotiationMinimum) {

        /** 10 % on parts and rates, 15 % on times, supplies 6 % (min 15 EUR), negotiate above 1 % and 5 EUR. */
        public static final Policy DEFAULT = new Policy(new BigDecimal("0.10"), new BigDecimal("0.15"),
                new BigDecimal("0.10"), new BigDecimal("0.06"), new BigDecimal("15.00"), new BigDecimal("0.01"),
                new BigDecimal("5.00"));
    }

    private final Tariff tariff;
    private final Policy policy;

    public QuoteAnalyzer(Tariff tariff, Policy policy) {
        this.tariff = tariff;
        this.policy = policy;
    }

    /** The same analyzer with other thresholds: the rules set by the administrators. */
    public QuoteAnalyzer withPolicy(Policy other) {
        return other.equals(policy) ? this : new QuoteAnalyzer(tariff, other);
    }

    public QuoteAnalyzer(Tariff tariff) {
        this(tariff, Policy.DEFAULT);
    }

    public QuoteAnalysis analyze(List<QuoteLineInput> lines, TariffClass tariffClass, Region region) {
        List<LineAnalysis> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        BigDecimal workReference = BigDecimal.ZERO;
        for (QuoteLineInput line : lines) {
            LineAnalysis result;
            if (line.kind() != LineKind.SUPPLIES && line.code() != null && !seen.add(line.kind() + ":" + line.code())) {
                result = duplicate(line);
            } else {
                result = switch (line.kind()) {
                    case PART -> part(line, tariffClass);
                    case LABOUR -> labour(line, tariffClass, region);
                    case SUPPLIES -> null;
                };
            }
            if (result != null) {
                workReference = workReference.add(result.referenceAmount());
            }
            results.add(result);
        }
        BigDecimal allowance = workReference.multiply(policy.suppliesShare()).max(policy.suppliesMinimum());
        for (int i = 0; i < lines.size(); i++) {
            if (results.get(i) == null) {
                LineAnalysis supplies = supplies(lines.get(i), allowance);
                allowance = allowance.subtract(supplies.referenceAmount());
                results.set(i, supplies);
            }
        }
        return summarize(results);
    }

    private QuoteAnalysis summarize(List<LineAnalysis> results) {
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal reference = BigDecimal.ZERO;
        BigDecimal overcharge = BigDecimal.ZERO;
        boolean unknown = false;
        for (LineAnalysis line : results) {
            amount = amount.add(line.amount());
            reference = reference.add(line.referenceAmount());
            if (line.flagged() && line.finding().isOvercharge()) {
                overcharge = overcharge.add(line.amount().subtract(line.referenceAmount()).max(BigDecimal.ZERO));
            }
            unknown |= line.finding() == Finding.UNKNOWN_ITEM;
        }
        BigDecimal threshold = amount.multiply(policy.negotiationShare()).max(policy.negotiationMinimum());
        Verdict verdict = overcharge.compareTo(threshold) > 0 ? Verdict.TO_NEGOTIATE
                : unknown ? Verdict.TO_CHECK : Verdict.COMPLIANT;
        return new QuoteAnalysis(results, money(amount), money(reference), money(overcharge), verdict);
    }

    private LineAnalysis part(QuoteLineInput line, TariffClass tariffClass) {
        BigDecimal amount = amount(line);
        Optional<PartPrice> reference = line.code() == null ? Optional.empty() : tariff.part(line.code(), tariffClass);
        if (reference.isEmpty()) {
            return unknown(line, amount, "Part absent from the reference tariff");
        }
        BigDecimal price = reference.get().price();
        BigDecimal referenceAmount = money(line.quantity().multiply(price));
        if (above(line.unitPrice(), price, policy.partTolerance())) {
            return new LineAnalysis(amount, line.quantity(), price, referenceAmount, Finding.OVERPRICED_PART,
                    "Unit price %s for %s at the reference tariff (+%s %%)".formatted(line.unitPrice(), price,
                            percent(line.unitPrice(), price)));
        }
        return new LineAnalysis(amount, line.quantity(), price, referenceAmount.min(amount), null, null);
    }

    private LineAnalysis labour(QuoteLineInput line, TariffClass tariffClass, Region region) {
        BigDecimal amount = amount(line);
        Optional<StandardTime> operation = line.code() == null ? Optional.empty()
                : tariff.operation(line.code(), tariffClass);
        if (operation.isEmpty()) {
            return unknown(line, amount, "Operation absent from the reference tariff");
        }
        BigDecimal standard = operation.get().hours();
        Optional<BigDecimal> negotiated = tariff.hourlyRate(region, operation.get().category(), tariffClass);
        BigDecimal rate = negotiated.orElse(line.unitPrice());
        BigDecimal referenceAmount = money(line.quantity().min(standard).multiply(line.unitPrice().min(rate)));
        boolean excessiveTime = above(line.quantity(), standard, policy.timeTolerance());
        boolean overpricedRate = negotiated.isPresent() && above(line.unitPrice(), rate, policy.rateTolerance());
        if (excessiveTime) {
            String detail = "%s h quoted for a standard time of %s h".formatted(line.quantity(), standard);
            if (overpricedRate) {
                detail += ", hourly rate %s for %s negotiated".formatted(line.unitPrice(), rate);
            }
            return new LineAnalysis(amount, standard, rate, referenceAmount, Finding.EXCESSIVE_TIME, detail);
        }
        if (overpricedRate) {
            return new LineAnalysis(amount, standard, rate, referenceAmount, Finding.OVERPRICED_RATE,
                    "Hourly rate %s for %s negotiated in the region (+%s %%)".formatted(line.unitPrice(), rate,
                            percent(line.unitPrice(), rate)));
        }
        return new LineAnalysis(amount, standard, rate, referenceAmount, null, null);
    }

    private LineAnalysis supplies(QuoteLineInput line, BigDecimal allowance) {
        BigDecimal amount = amount(line);
        BigDecimal allowed = money(allowance.max(BigDecimal.ZERO));
        if (amount.compareTo(allowed) > 0) {
            return new LineAnalysis(amount, BigDecimal.ONE, allowed, allowed, Finding.EXCESSIVE_SUPPLIES,
                    "Supplies of %s for %s allowed on this work".formatted(amount, allowed));
        }
        return new LineAnalysis(amount, BigDecimal.ONE, allowed, amount, null, null);
    }

    private static LineAnalysis duplicate(QuoteLineInput line) {
        return new LineAnalysis(amount(line), line.quantity(), line.unitPrice(), BigDecimal.ZERO.setScale(2),
                Finding.DUPLICATE, "Code " + line.code() + " is already billed on a previous line");
    }

    private static LineAnalysis unknown(QuoteLineInput line, BigDecimal amount, String detail) {
        return new LineAnalysis(amount, null, null, amount, Finding.UNKNOWN_ITEM, detail);
    }

    private static boolean above(BigDecimal value, BigDecimal reference, BigDecimal tolerance) {
        return value.compareTo(reference.multiply(BigDecimal.ONE.add(tolerance))) > 0;
    }

    private static BigDecimal amount(QuoteLineInput line) {
        return money(line.quantity().multiply(line.unitPrice()));
    }

    private static BigDecimal percent(BigDecimal value, BigDecimal reference) {
        return value.subtract(reference).multiply(HUNDRED).divide(reference, 0, RoundingMode.HALF_UP);
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
