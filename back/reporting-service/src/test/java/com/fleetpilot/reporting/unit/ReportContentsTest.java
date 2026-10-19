package com.fleetpilot.reporting.unit;

import static com.fleetpilot.reporting.unit.FakeSources.COMPANY;
import static com.fleetpilot.reporting.unit.FakeSources.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.documents.PdfRenderer;
import com.fleetpilot.reporting.application.ReportContents;
import com.fleetpilot.reporting.application.ReportContents.Built;
import com.fleetpilot.reporting.domain.Report;
import com.fleetpilot.reporting.domain.ReportType;
import com.fleetpilot.reporting.infrastructure.excel.PoiWorkbookWriter;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@DisplayName("Report files")
class ReportContentsTest {

    private final FakeSources sources = new FakeSources();
    private final ReportContents contents = new ReportContents(sources, sources, new PoiWorkbookWriter(),
            new PdfRenderer(engine()), Clock.fixed(NOW, ZoneId.of("Europe/Paris")));

    static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static Report report(ReportType type) {
        return new Report(UUID.randomUUID(), type, "Néolia Énergies", COMPANY, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 10, 14), new Report.Requester("m.roux", "m.roux@neolia-energies.fr", "Mathilde Roux"),
                NOW);
    }

    private static XSSFWorkbook open(Built built) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(built.file()));
    }

    @Test
    void the_inventory_lists_every_vehicle_with_french_labels() throws IOException {
        Built built = contents.build(report(ReportType.FLEET_INVENTORY));
        assertThat(built.rows()).isEqualTo(4);
        try (XSSFWorkbook workbook = open(built)) {
            Sheet sheet = workbook.getSheet("Parc");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Inventaire du parc · Néolia Énergies");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("Immatriculation");
            assertThat(sheet.getRow(4).getCell(4).getStringCellValue()).isEqualTo("Essence");
            assertThat(sheet.getRow(4).getCell(6).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
    }

    @Test
    void the_tco_has_a_summary_and_typed_amounts_with_totals() throws IOException {
        Built built = contents.build(report(ReportType.TCO));
        assertThat(built.rows()).isEqualTo(3);
        try (XSSFWorkbook workbook = open(built)) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Synthèse");
            Sheet detail = workbook.getSheet("TCO par véhicule");
            assertThat(detail.getRow(4).getCell(0).getStringCellValue()).isEqualTo("GH-482-KM");
            assertThat(detail.getRow(4).getCell(8).getNumericCellValue()).isEqualTo(6300.0);
            assertThat(detail.getRow(7).getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(detail.getRow(7).getCell(8).getNumericCellValue()).isEqualTo(11000.0);
        }
    }

    @Test
    void the_quote_export_covers_the_period_with_totals() throws IOException {
        Built built = contents.build(report(ReportType.QUOTES));
        try (XSSFWorkbook workbook = open(built)) {
            Sheet sheet = workbook.getSheet("Devis");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains("du 2026-01-01 au 2026-10-14");
            assertThat(sheet.getRow(4).getCell(5).getStringCellValue()).isEqualTo("Facturé");
            assertThat(sheet.getRow(8).getCell(2).getStringCellValue()).isEqualTo("4 devis");
            assertThat(sheet.getRow(8).getCell(6).getNumericCellValue()).isEqualTo(1970.0);
        }
    }

    @Test
    void the_deadlines_and_the_savings_report_are_built() throws IOException {
        Built deadlines = contents.build(report(ReportType.DEADLINES));
        assertThat(deadlines.rows()).isEqualTo(2);
        try (XSSFWorkbook workbook = open(deadlines)) {
            assertThat(workbook.getSheet("Échéances").getRow(4).getCell(5).getStringCellValue()).isEqualTo("En retard");
        }
        Built savings = contents.build(report(ReportType.SAVINGS));
        Files.createDirectories(Path.of("target", "documents"));
        Files.write(Path.of("target", "documents", "rapport-economies.pdf"), savings.file());
        assertThat(new String(savings.file(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(savings.rows()).isEqualTo(750);
    }

    @Test
    void a_report_follows_its_lifecycle_and_names_its_file() {
        Report report = report(ReportType.TCO);
        assertThat(report.getPeriodFrom()).as("the TCO covers no period").isNull();
        report.start(NOW);
        assertThatThrownBy(() -> report.start(NOW)).isInstanceOf(BusinessRuleException.class);
        report.ready(2048, 3, NOW);
        assertThat(report.getStatus()).isEqualTo(Report.Status.READY);
        assertThat(report.getFileName()).isEqualTo("cout-total-de-detention-tco-2026-10-14.xlsx");
        assertThat(report.getContentType()).contains("spreadsheetml");
    }

    @Test
    void a_periodic_report_needs_a_valid_period() {
        Report.Requester requester = new Report.Requester("m.roux", null, null);
        assertThatThrownBy(() -> new Report(UUID.randomUUID(), ReportType.QUOTES, null, null, null, null, requester, NOW))
                .hasMessageContaining("period");
        assertThatThrownBy(() -> new Report(UUID.randomUUID(), ReportType.QUOTES, null, null, LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 1, 1), requester, NOW)).hasMessageContaining("before its end");
    }
}
