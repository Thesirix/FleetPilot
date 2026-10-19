package com.fleetpilot.reporting.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.fleetpilot.documents.DocumentFormats;
import com.fleetpilot.documents.PdfRenderer;
import com.fleetpilot.reporting.application.Sources.Deadline;
import com.fleetpilot.reporting.application.Sources.FleetSource;
import com.fleetpilot.reporting.application.Sources.Quote;
import com.fleetpilot.reporting.application.Sources.QuoteSource;
import com.fleetpilot.reporting.application.Sources.Vehicle;
import com.fleetpilot.reporting.application.TcoCalculator.Indicators;
import com.fleetpilot.reporting.application.TcoCalculator.VehicleCost;
import com.fleetpilot.reporting.application.Tables.Column;
import com.fleetpilot.reporting.application.Tables.Kind;
import com.fleetpilot.reporting.application.Tables.Sheet;
import com.fleetpilot.reporting.application.Tables.WorkbookWriter;
import com.fleetpilot.reporting.domain.Report;
import org.springframework.stereotype.Component;

/** Builds the file of each kind of report from the data of the fleet and quote services. */
@Component
public class ReportContents {

    /** A built file and the number of data rows it holds. */
    public record Built(byte[] file, int rows) {
    }

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final FleetSource fleet;
    private final QuoteSource quotes;
    private final WorkbookWriter workbooks;
    private final PdfRenderer pdf;
    private final Clock clock;

    public ReportContents(FleetSource fleet, QuoteSource quotes, WorkbookWriter workbooks, PdfRenderer pdf, Clock clock) {
        this.fleet = fleet;
        this.quotes = quotes;
        this.workbooks = workbooks;
        this.pdf = pdf;
        this.clock = clock;
    }

    public Built build(Report report) {
        String scope = report.getCompanyId() == null ? "Toutes les entreprises clientes"
                : fleet.companyName(report.getCompanyId());
        String subtitle = scope + " · édité le " + LocalDate.now(clock);
        return switch (report.getType()) {
            case FLEET_INVENTORY -> inventory(report, subtitle);
            case TCO -> tco(report, subtitle);
            case QUOTES -> quotes(report, subtitle);
            case DEADLINES -> deadlines(report, subtitle);
            case SAVINGS -> savings(report, scope);
        };
    }

    // ---------------------------------------------------------------- inventory

    private Built inventory(Report report, String subtitle) {
        List<Vehicle> vehicles = fleet.vehicles(report.getCompanyId());
        List<Column> columns = List.of(new Column("Immatriculation", Kind.TEXT, 14), new Column("Marque", Kind.TEXT, 14),
                new Column("Modèle", Kind.TEXT, 22), new Column("Catégorie", Kind.TEXT, 10),
                new Column("Énergie", Kind.TEXT, 18), new Column("Crit'Air", Kind.TEXT, 9),
                new Column("Kilométrage", Kind.INTEGER, 13), new Column("Statut", Kind.TEXT, 14),
                new Column("Site", Kind.TEXT, 32), new Column("Centre de coût", Kind.TEXT, 24),
                new Column("Conducteur", Kind.TEXT, 24));
        List<List<Object>> rows = vehicles.stream().map(v -> List.<Object>of(v.registration(), text(v.make()),
                text(v.model()), text(Labels.of(v.category())), text(Labels.of(v.energy())),
                v.critAir() == null ? "" : v.critAir() == 0 ? "E" : String.valueOf(v.critAir()),
                v.mileage() == null ? 0 : v.mileage(), text(Labels.of(v.status())),
                v.site() == null ? "" : text(v.site().name()), v.costCenter() == null ? "" : text(v.costCenter().label()),
                v.currentDriver() == null ? "" : text(v.currentDriver().fullName()))).toList();
        Sheet sheet = new Sheet("Parc", report.getTitle(), subtitle + " · " + rows.size() + " véhicules", columns, rows,
                List.of());
        return new Built(workbooks.write(List.of(sheet)), rows.size());
    }

    // ---------------------------------------------------------------- TCO

    private Built tco(Report report, String subtitle) {
        UUID company = report.getCompanyId();
        List<Vehicle> vehicles = fleet.vehicles(company);
        TcoCalculator calculator = new TcoCalculator(clock.instant(), PARIS);
        List<VehicleCost> costs = calculator.costs(vehicles, fleet.activeContracts(company),
                quotes.quotes(company, null, null), fleet.incidents(company));
        Indicators indicators = calculator.indicators(vehicles, costs, fleet.incidents(company));
        List<Column> columns = List.of(new Column("Immatriculation", Kind.TEXT, 14), new Column("Véhicule", Kind.TEXT, 28),
                new Column("Énergie", Kind.TEXT, 16), new Column("Loueur", Kind.TEXT, 12),
                new Column("Loyer mensuel", Kind.MONEY, 14), new Column("Loyers 12 mois", Kind.MONEY, 15),
                new Column("Entretien 12 mois", Kind.MONEY, 16), new Column("Sinistres 12 mois", Kind.MONEY, 16),
                new Column("Coût total 12 mois", Kind.MONEY, 17), new Column("Km par an", Kind.INTEGER, 11),
                new Column("Coût au km", Kind.DECIMAL, 11));
        List<List<Object>> rows = costs.stream().map(c -> {
            List<Object> row = new ArrayList<>();
            row.add(c.vehicle().registration());
            row.add((text(c.vehicle().make()) + " " + text(c.vehicle().model())).trim());
            row.add(text(Labels.of(c.vehicle().energy())));
            row.add(c.lessor() == null ? "Achat" : c.lessor());
            row.add(c.monthlyRent());
            row.add(c.rent());
            row.add(c.maintenance());
            row.add(c.claims());
            row.add(c.total());
            row.add(c.kilometres());
            row.add(c.costPerKm());
            return row;
        }).toList();
        List<Object> totals = new ArrayList<>(List.of("Total", indicators.vehicles() + " véhicules", "", ""));
        totals.add(null);
        totals.addAll(List.of(indicators.rent(), indicators.maintenance(), indicators.claims(), indicators.totalCost()));
        totals.add(null);
        totals.add(indicators.costPerKm());
        Sheet detail = new Sheet("TCO par véhicule", report.getTitle(), subtitle, columns, rows, totals);
        Sheet summary = new Sheet("Synthèse", "Indicateurs du parc sur douze mois", subtitle,
                List.of(new Column("Indicateur", Kind.TEXT, 42), new Column("Valeur", Kind.DECIMAL, 18)),
                List.of(List.of("Véhicules en parc", BigDecimal.valueOf(indicators.vehicles())),
                        List.of("Coût total sur 12 mois (€)", indicators.totalCost()),
                        List.of("Coût mensuel moyen par véhicule (€)", indicators.monthlyCostPerVehicle()),
                        List.of("Coût au kilomètre (€)", orZero(indicators.costPerKm())),
                        List.of("Loyers (€)", indicators.rent()), List.of("Entretien et réparations (€)",
                                indicators.maintenance()), List.of("Sinistres (€)", indicators.claims()),
                        List.of("Sinistres pour 100 véhicules", indicators.claimsPer100Vehicles()),
                        List.of("Taux d'immobilisation (%)", indicators.immobilisationRate()),
                        List.of("Part de véhicules en location (%)", indicators.leasedShare())), List.of());
        return new Built(workbooks.write(List.of(summary, detail)), rows.size());
    }

    // ---------------------------------------------------------------- quotes

    private Built quotes(Report report, String subtitle) {
        List<Quote> list = quotes.quotes(report.getCompanyId(), report.getPeriodFrom().atStartOfDay(PARIS).toInstant(),
                report.getPeriodTo().plusDays(1).atStartOfDay(PARIS).toInstant());
        List<Column> columns = List.of(new Column("Référence", Kind.TEXT, 17), new Column("Reçu le", Kind.DATE, 12),
                new Column("Garage", Kind.TEXT, 28), new Column("Ville", Kind.TEXT, 16),
                new Column("Immatriculation", Kind.TEXT, 14), new Column("Statut", Kind.TEXT, 12),
                new Column("Montant HT", Kind.MONEY, 13), new Column("Tarif de référence", Kind.MONEY, 16),
                new Column("Surcoût détecté", Kind.MONEY, 15), new Column("Montant accepté", Kind.MONEY, 15),
                new Column("Économie", Kind.MONEY, 12), new Column("Anomalies", Kind.INTEGER, 10));
        List<List<Object>> rows = list.stream().map(q -> {
            List<Object> row = new ArrayList<>();
            row.add(q.reference());
            row.add(LocalDate.ofInstant(q.submittedAt(), PARIS));
            row.add(q.garage() == null ? "" : q.garage().name());
            row.add(q.garage() == null ? "" : q.garage().city());
            row.add(q.registration());
            row.add(Labels.of(q.status()));
            row.add(q.amount());
            row.add(q.referenceAmount());
            row.add(q.overcharge());
            row.add(q.acceptedAmount());
            row.add(q.savings());
            row.add(q.findingCount());
            return row;
        }).toList();
        List<Object> totals = new ArrayList<>(List.of("Total", "", rows.size() + " devis", "", "", ""));
        totals.addAll(List.of(sum(list, Quote::amount), sum(list, Quote::referenceAmount), sum(list, Quote::overcharge),
                sum(list, Quote::acceptedAmount), sum(list, Quote::savings)));
        totals.add(null);
        String period = " · du " + report.getPeriodFrom() + " au " + report.getPeriodTo();
        return new Built(workbooks.write(List.of(new Sheet("Devis", report.getTitle(), subtitle + period, columns, rows,
                totals))), rows.size());
    }

    // ---------------------------------------------------------------- deadlines

    private Built deadlines(Report report, String subtitle) {
        List<Deadline> list = fleet.urgentDeadlines(report.getCompanyId());
        List<Column> columns = List.of(new Column("Immatriculation", Kind.TEXT, 14), new Column("Véhicule", Kind.TEXT, 28),
                new Column("Échéance", Kind.TEXT, 20), new Column("Date limite", Kind.DATE, 12),
                new Column("Kilométrage limite", Kind.INTEGER, 17), new Column("Urgence", Kind.TEXT, 11),
                new Column("Jours restants", Kind.INTEGER, 14));
        List<List<Object>> rows = list.stream().map(d -> {
            List<Object> row = new ArrayList<>();
            row.add(d.vehicle().registration());
            row.add((text(d.vehicle().make()) + " " + text(d.vehicle().model())).trim());
            row.add(Labels.of(d.type()));
            row.add(d.dueDate());
            row.add(d.dueMileage());
            row.add(Labels.of(d.urgency()));
            row.add(d.daysRemaining());
            return row;
        }).toList();
        return new Built(workbooks.write(List.of(new Sheet("Échéances", report.getTitle(), subtitle, columns, rows,
                List.of()))), rows.size());
    }

    // ---------------------------------------------------------------- savings (PDF)

    private Built savings(Report report, String scope) {
        Map<String, Object> summary = quotes.savings(report.getCompanyId());
        Map<String, Object> model = new HashMap<>();
        model.put("s", summary);
        model.put("scope", scope);
        model.put("title", report.getTitle());
        model.put("today", LocalDate.now(clock));
        model.put("fmt", DocumentFormats.INSTANCE);
        model.put("maxSavings", maxMonthlySavings(summary));
        Object quoteCount = summary.get("quotes");
        return new Built(pdf.render("pdf/savings-report", model, Locale.FRANCE),
                quoteCount instanceof Number n ? n.intValue() : 0);
    }

    // ---------------------------------------------------------------- helpers

    /** Longest bar of the monthly chart of the PDF. */
    static double maxMonthlySavings(Map<String, Object> summary) {
        if (!(summary.get("months") instanceof List<?> months)) {
            return 0;
        }
        return months.stream().filter(Map.class::isInstance).map(m -> ((Map<?, ?>) m).get("savings"))
                .filter(Number.class::isInstance).mapToDouble(v -> ((Number) v).doubleValue()).max().orElse(0);
    }

    private static BigDecimal sum(List<Quote> list, Function<Quote, BigDecimal> field) {
        return list.stream().map(field).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
