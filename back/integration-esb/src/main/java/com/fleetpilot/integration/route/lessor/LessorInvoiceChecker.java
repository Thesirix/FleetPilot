package com.fleetpilot.integration.route.lessor;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.integration.fleet.FleetDirectory;
import com.fleetpilot.integration.fleet.FleetDirectory.LeaseContract;
import com.fleetpilot.integration.journal.Outcome;
import com.fleetpilot.integration.journal.Outcome.Issue;
import com.fleetpilot.integration.route.Csv;
import org.springframework.stereotype.Component;

/**
 * Checks a lessor's monthly invoice line by line against the lease contracts known by FleetPilot: this is where a
 * fleet manager saves money, by contesting what the lessor bills but does not owe.
 *
 * <ul>
 *   <li>unknown contract, or a contract on another vehicle: the line is contested in full;</li>
 *   <li>rent of a month after the end of the contract, or before its start: contested in full;</li>
 *   <li>the same contract billed twice for the same month: the second line is contested;</li>
 *   <li>rent different from the contract (more than 0.50 euro): the difference is contested.</li>
 * </ul>
 */
@Component
public class LessorInvoiceChecker {

    static final BigDecimal TOLERANCE = new BigDecimal("0.50");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRANCE);

    private final FleetDirectory fleet;

    public LessorInvoiceChecker(FleetDirectory fleet) {
        this.fleet = fleet;
    }

    public Outcome check(List<Map<String, String>> rows) {
        if (rows.isEmpty()) {
            return Outcome.rejected(null, Issue.error(null, null, "EMPTY_FILE", "Le fichier ne contient aucune ligne"),
                    "Facture vide");
        }
        String lessor = Csv.optional(rows.getFirst(), "loueur");
        String invoice = Csv.optional(rows.getFirst(), "numero_facture");
        Map<String, LeaseContract> contracts = lessor == null ? Map.of() : fleet.contracts(lessor).stream()
                .collect(Collectors.toMap(LeaseContract::contractNumber, Function.identity(), (a, b) -> a));

        List<Issue> issues = new ArrayList<>();
        Set<String> billed = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal contested = BigDecimal.ZERO;
        int accepted = 0;
        for (int i = 0; i < rows.size(); i++) {
            int line = i + 2;
            Map<String, String> row = rows.get(i);
            String contractNumber = row.get("numero_contrat");
            try {
                Line billedLine = Line.of(row);
                total = total.add(billedLine.total());
                Issue issue = check(line, billedLine, contracts.get(billedLine.contract()), billed);
                if (issue == null) {
                    accepted++;
                } else {
                    issues.add(issue);
                    contested = contested.add("RENT_MISMATCH".equals(issue.code())
                            ? billedLine.rent().subtract(contracts.get(billedLine.contract()).monthlyRent()).max(BigDecimal.ZERO)
                            : billedLine.total());
                }
            } catch (IllegalArgumentException | DateTimeParseException e) {
                issues.add(Issue.error(line, contractNumber, "UNREADABLE_LINE", "Ligne illisible : " + e.getMessage()));
            }
        }
        String summary = "%s, facture %s : %d loyers, %s HT".formatted(lessor, invoice, rows.size(), money(total))
                + (issues.isEmpty() ? ", conforme aux contrats"
                        : ", %d ligne%s à contester (%s)".formatted(issues.size(), issues.size() > 1 ? "s" : "",
                                money(contested)));
        return new Outcome(lessor, rows.size(), accepted, issues, summary, invoice);
    }

    private static Issue check(int line, Line billed, LeaseContract contract, Set<String> alreadyBilled) {
        String ref = billed.contract();
        if (contract == null) {
            return Issue.error(line, ref, "UNKNOWN_CONTRACT", "Contrat %s inconnu de FleetPilot : %s contesté"
                    .formatted(ref, money(billed.total())));
        }
        if (!contract.registration().equals(billed.registration())) {
            return Issue.error(line, ref, "REGISTRATION_MISMATCH", "Le contrat %s porte sur %s, la facture sur %s"
                    .formatted(ref, contract.registration(), billed.registration()));
        }
        LocalDate end = contract.endedOn() != null ? contract.endedOn() : contract.endDate();
        if (billed.period().atDay(1).isAfter(end)) {
            return Issue.error(line, ref, "CONTRACT_ENDED", "Contrat terminé le %s : le loyer de %s n'est pas dû"
                    .formatted(DAY.format(end), MONTH.format(billed.period())));
        }
        if (billed.period().atEndOfMonth().isBefore(contract.startDate())) {
            return Issue.error(line, ref, "CONTRACT_NOT_STARTED", "Le contrat ne commence que le %s"
                    .formatted(DAY.format(contract.startDate())));
        }
        if (!alreadyBilled.add(ref + "|" + billed.period())) {
            return Issue.error(line, ref, "DUPLICATE_LINE", "Loyer de %s facturé deux fois"
                    .formatted(MONTH.format(billed.period())));
        }
        BigDecimal difference = billed.rent().subtract(contract.monthlyRent());
        if (difference.abs().compareTo(TOLERANCE) > 0) {
            return Issue.warning(line, ref, "RENT_MISMATCH", "Loyer facturé %s pour %s au contrat (%s%s)".formatted(
                    money(billed.rent()), money(contract.monthlyRent()), difference.signum() > 0 ? "+" : "",
                    money(difference)));
        }
        return null;
    }

    /** One billed rent. */
    record Line(String contract, String registration, YearMonth period, BigDecimal rent, BigDecimal services,
            BigDecimal total) {

        static Line of(Map<String, String> row) {
            return new Line(Csv.text(row, "numero_contrat"), Csv.text(row, "immatriculation").toUpperCase(Locale.ROOT),
                    YearMonth.parse(Csv.text(row, "periode")), Csv.decimal(row, "loyer_ht"),
                    Csv.decimal(row, "prestations_ht"), Csv.decimal(row, "total_ht"));
        }
    }

    static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.FRANCE).format(amount);
    }
}
