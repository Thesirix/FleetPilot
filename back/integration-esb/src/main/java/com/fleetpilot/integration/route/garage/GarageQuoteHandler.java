package com.fleetpilot.integration.route.garage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import com.fleetpilot.integration.fleet.BusinessServices.CreatedQuote;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteLine;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteModule;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteSubmission;
import com.fleetpilot.integration.fleet.BusinessServices.Refused;
import com.fleetpilot.integration.journal.Outcome;
import com.fleetpilot.integration.journal.Outcome.Issue;
import org.springframework.stereotype.Component;

/**
 * Turns a garage's quote file into a quote of the quote module. The schema already guaranteed the structure; this
 * checks what a schema cannot (the total announced by the garage is the sum of its lines) and translates the
 * vocabulary of the garages (PIECE, MAIN_OEUVRE, INGREDIENTS) into that of FleetPilot.
 */
@Component
public class GarageQuoteHandler {

    /** Rounding tolerated between the announced total and the sum of the lines. */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    private final QuoteModule quotes;

    public GarageQuoteHandler(QuoteModule quotes) {
        this.quotes = quotes;
    }

    public Outcome handle(GarageQuoteFile file) {
        String partner = file.issuer.name.trim();
        BigDecimal sum = file.lines.stream()
                .map(l -> l.quantity.multiply(l.unitPrice).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.subtract(file.totalExcludingTax).abs().compareTo(TOLERANCE) > 0) {
            return Outcome.rejected(partner, Issue.error(null, file.reference, "TOTAL_MISMATCH",
                    "Total HT annoncé %s, somme des lignes %s".formatted(money(file.totalExcludingTax), money(sum))),
                    "Devis %s refusé : total incohérent".formatted(file.reference));
        }
        QuoteSubmission submission = new QuoteSubmission(file.issuer.siret, file.vehicle.registration, file.reference,
                file.vehicle.mileage, file.subject.trim(), file.lines.stream().map(GarageQuoteHandler::line).toList());
        try {
            CreatedQuote created = quotes.submit(submission);
            String vehicle = created.vehicle() == null ? file.vehicle.registration + " (véhicule à identifier)"
                    : created.vehicle().registration();
            List<Issue> issues = created.vehicle() == null
                    ? List.of(Issue.warning(null, file.reference, "UNKNOWN_VEHICLE",
                            "Immatriculation " + file.vehicle.registration + " inconnue du parc : le devis attend "
                                    + "l'identification du véhicule"))
                    : List.of();
            return new Outcome(partner, 1, 1, issues, "Devis %s créé : %s, %s HT".formatted(created.reference(),
                    vehicle, money(created.amount())), created.reference());
        } catch (Refused refused) {
            return Outcome.rejected(partner, Issue.error(null, file.reference, refused.code(), refused.getMessage()),
                    "Devis %s refusé par le module devis".formatted(file.reference));
        }
    }

    private static QuoteLine line(GarageQuoteFile.Line line) {
        String kind = switch (line.nature) {
            case "MAIN_OEUVRE" -> "LABOUR";
            case "INGREDIENTS" -> "SUPPLIES";
            default -> "PART";
        };
        return new QuoteLine(kind, line.code, line.label.trim(), line.quantity, line.unitPrice);
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.FRANCE);
        return amount == null ? "-" : format.format(amount);
    }
}
