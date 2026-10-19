package com.fleetpilot.integration.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.integration.fleet.FleetDirectory;
import com.fleetpilot.integration.journal.ExchangeStatus;
import com.fleetpilot.integration.journal.Outcome;
import com.fleetpilot.integration.journal.Outcome.Issue;
import com.fleetpilot.integration.route.lessor.LessorInvoiceChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("A lessor invoice checked against the lease contracts")
class LessorInvoiceCheckerTest {

    private static final FleetDirectory.LeaseContract ACTIVE = contract("ARV-2023-104280", "GH-482-KM", "474.89",
            LocalDate.of(2023, 7, 1), null);
    private static final FleetDirectory.LeaseContract RETURNED = contract("ARV-2022-100120", "EF-210-PA", "389.00",
            LocalDate.of(2022, 3, 1), LocalDate.of(2026, 8, 12));
    private static final FleetDirectory.LeaseContract OTHER = contract("ARV-2024-106002", "FT-118-BC", "512.40",
            LocalDate.of(2024, 1, 15), null);

    private final LessorInvoiceChecker checker = new LessorInvoiceChecker(new FleetDirectory() {
        @Override
        public List<FleetVehicle> vehicles() {
            return List.of();
        }

        @Override
        public List<LeaseContract> contracts(String lessor) {
            return "Arval".equals(lessor) ? List.of(ACTIVE, RETURNED, OTHER) : List.of();
        }
    });

    private static FleetDirectory.LeaseContract contract(String number, String registration, String rent,
            LocalDate start, LocalDate endedOn) {
        return new FleetDirectory.LeaseContract(UUID.randomUUID(), "Arval", number, UUID.randomUUID(), registration,
                start, start.plusYears(4), new BigDecimal(rent), endedOn == null ? "ACTIVE" : "ENDED", endedOn);
    }

    private static Map<String, String> line(String contract, String registration, String period, String rent) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("loueur", "Arval");
        row.put("numero_facture", "ARV-F-202609-0412");
        row.put("numero_contrat", contract);
        row.put("immatriculation", registration);
        row.put("periode", period);
        row.put("loyer_ht", rent);
        row.put("prestations_ht", "38,00");
        row.put("total_ht", new BigDecimal(rent.replace(',', '.')).add(new BigDecimal("38.00")).toPlainString()
                .replace('.', ','));
        return row;
    }

    @Test
    void an_invoice_conforming_to_the_contracts_is_accepted_line_by_line() {
        Outcome outcome = checker.check(List.of(line("ARV-2023-104280", "GH-482-KM", "2026-09", "474,89"),
                line("ARV-2024-106002", "FT-118-BC", "2026-09", "512,40")));

        assertThat(outcome.status()).isEqualTo(ExchangeStatus.PROCESSED);
        assertThat(outcome.partner()).isEqualTo("Arval");
        assertThat(outcome.resultRef()).isEqualTo("ARV-F-202609-0412");
        assertThat(outcome.summary()).isEqualTo("Arval, facture ARV-F-202609-0412 : 2 loyers, 1\u202f063,29\u00a0€ HT, "
                + "conforme aux contrats");
    }

    @Test
    void what_the_lessor_bills_but_is_not_owed_is_listed_with_the_amount_to_contest() {
        Outcome outcome = checker.check(List.of(
                line("ARV-2023-104280", "GH-482-KM", "2026-09", "498,63"),
                line("ARV-2022-100120", "EF-210-PA", "2026-09", "389,00"),
                line("ARV-2024-199871", "GA-731-PL", "2026-09", "512,40"),
                line("ARV-2024-106002", "FT-118-BC", "2026-09", "512,40"),
                line("ARV-2024-106002", "FT-118-BC", "2026-09", "512,40"),
                line("ARV-2024-106002", "AB-123-CD", "2026-10", "512,40"),
                line("ARV-2024-106002", "FT-118-BC", "septembre", "512,40")));

        assertThat(outcome.status()).isEqualTo(ExchangeStatus.PARTIAL);
        assertThat(outcome.records()).isEqualTo(7);
        assertThat(outcome.accepted()).isOne();
        assertThat(outcome.issues()).extracting(Issue::line, Issue::severity, Issue::code).containsExactly(
                tuple(2, Issue.Severity.WARNING, "RENT_MISMATCH"),
                tuple(3, Issue.Severity.ERROR, "CONTRACT_ENDED"),
                tuple(4, Issue.Severity.ERROR, "UNKNOWN_CONTRACT"),
                tuple(6, Issue.Severity.ERROR, "DUPLICATE_LINE"),
                tuple(7, Issue.Severity.ERROR, "REGISTRATION_MISMATCH"),
                tuple(8, Issue.Severity.ERROR, "UNREADABLE_LINE"));
        assertThat(outcome.issues().getFirst().message())
                .isEqualTo("Loyer facturé 498,63\u00a0€ pour 474,89\u00a0€ au contrat (+23,74\u00a0€)");
        assertThat(outcome.issues().get(1).message())
                .isEqualTo("Contrat terminé le 12 août 2026 : le loyer de septembre 2026 n'est pas dû");
        assertThat(outcome.summary()).endsWith("6 lignes à contester (2\u202f101,94\u00a0€)");
    }

    @Test
    void an_empty_invoice_is_refused() {
        assertThat(checker.check(List.of()).status()).isEqualTo(ExchangeStatus.REJECTED);
    }
}
