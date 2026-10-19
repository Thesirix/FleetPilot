package com.fleetpilot.portal.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.portal.api.Contract;
import org.junit.jupiter.api.Test;

class ContractListTest {

    private static Contract contract(String number, String registration, long daysRemaining, Integer overrunKm) {
        return new Contract(UUID.randomUUID(), number, UUID.randomUUID(), registration, "Peugeot", "e-208",
                LocalDate.of(2024, 1, 15), LocalDate.of(2026, 10, 16).plusDays(daysRemaining), 36, 90_000, 12,
                new BigDecimal("412.50"), true, false, true, daysRemaining,
                overrunKm == null ? null : 90_000 + overrunKm, overrunKm, overrunKm != null && overrunKm > 0);
    }

    private final List<Contract> contracts = List.of(
            contract("ARV-2023-100001", "GH-482-KM", 400, null),
            contract("ARV-2023-100002", "FW-567-ZW", 30, 12_000),
            contract("ARV-2023-100003", "GG-385-XT", 80, null),
            contract("ARV-2023-100004", "GY-727-VQ", 600, 25_000));

    @Test
    void the_figures_cover_every_active_contract() {
        ContractList list = ContractList.of(contracts, null, null, 1);

        assertThat(list.active()).isEqualTo(4);
        assertThat(list.returnsSoon()).isEqualTo(2);
        assertThat(list.overrunRisk()).isEqualTo(2);
        assertThat(list.projectedOverrun()).isEqualTo("37 000 km");
        assertThat(list.filter()).isEqualTo(ContractList.ALL);
        assertThat(list.rows()).extracting(ContractRow::contractNumber)
                .containsExactly("ARV-2023-100002", "ARV-2023-100003", "ARV-2023-100001", "ARV-2023-100004");
    }

    @Test
    void the_overruns_come_largest_first_and_the_returns_by_end_date() {
        assertThat(ContractList.of(contracts, ContractList.OVERRUN, null, 1).rows())
                .extracting(ContractRow::registration).containsExactly("GY-727-VQ", "FW-567-ZW");
        assertThat(ContractList.of(contracts, ContractList.RETURNS, null, 1).rows())
                .extracting(ContractRow::registration).containsExactly("FW-567-ZW", "GG-385-XT");
        assertThat(ContractList.of(contracts, "anything", null, 1).filter()).isEqualTo(ContractList.ALL);
    }

    @Test
    void a_registration_is_found_whatever_its_spelling() {
        assertThat(ContractList.of(contracts, null, "gh 482km", 1).rows())
                .extracting(ContractRow::contractNumber).containsExactly("ARV-2023-100001");
        assertThat(ContractList.of(contracts, null, "100003", 1).matching()).isEqualTo(1);
    }

    @Test
    void the_list_is_cut_in_pages_of_25() {
        List<Contract> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            many.add(contract("ARV-2024-%06d".formatted(i), "AA-%03d-AA".formatted(i), 100 + i, null));
        }
        ContractList third = ContractList.of(many, null, null, 3);
        assertThat(third.pages()).isEqualTo(3);
        assertThat(third.rows()).hasSize(10);
        assertThat(ContractList.of(many, null, null, 99).page()).isEqualTo(3);
        assertThat(ContractList.of(many, null, null, -2).page()).isEqualTo(1);
    }

    @Test
    void a_row_is_written_the_french_way() {
        ContractRow row = ContractRow.of(contract("ARV-2023-100002", "FW-567-ZW", 30, 12_000));
        assertThat(row.remaining()).isEqualTo("30 jours");
        assertThat(row.returnSoon()).isTrue();
        assertThat(row.contractualMileage()).isEqualTo("90 000 km");
        assertThat(row.projectedOverrun()).isEqualTo("+12 000 km");
        assertThat(row.monthlyRent()).isEqualTo("412,50 €");
        assertThat(row.services()).isEqualTo("Entretien, Véhicule de remplacement");
        assertThat(row.endDate()).isEqualTo("15/11/2026");
    }

    @Test
    void the_export_is_ready_for_excel() {
        String csv = ExportServlet.csv(List.of(contract("ARV-2023-100002", "FW-567-ZW", 30, 12_000)));
        assertThat(csv).startsWith("﻿Contrat;Immatriculation;");
        assertThat(csv).contains("ARV-2023-100002;FW-567-ZW;Peugeot e-208;15/01/2024;15/11/2026;30;90000;12;102000;12000;"
                + "412,50;oui;non;oui\r\n");
        assertThat(ExportServlet.cell("Garage; \"Le Prado\"")).isEqualTo("\"Garage; \"\"Le Prado\"\"\"");
        // A cell that a spreadsheet would run as a formula is neutralized
        assertThat(ExportServlet.cell("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
    }

    @Test
    void a_reading_is_typed_freely() {
        assertThat(ReadingServlet.mileage("45 210")).isEqualTo(45_210);
        assertThat(ReadingServlet.mileage("45 210 km")).isEqualTo(45_210);
        assertThat(ReadingServlet.mileage("45.210")).isEqualTo(45_210);
        assertThat(ReadingServlet.mileage("quarante")).isNull();
        assertThat(ReadingServlet.mileage("-12")).isNull();
        assertThat(ReadingServlet.readAt("2026-10-16T09:30")).isEqualTo(Instant.parse("2026-10-16T07:30:00Z"));
        assertThat(ReadingServlet.readAt("hier")).isNull();
    }
}
