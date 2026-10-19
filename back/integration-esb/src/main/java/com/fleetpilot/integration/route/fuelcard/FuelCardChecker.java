package com.fleetpilot.integration.route.fuelcard;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.integration.fleet.FleetDirectory;
import com.fleetpilot.integration.fleet.FleetDirectory.FleetVehicle;
import com.fleetpilot.integration.journal.FileIntake;
import com.fleetpilot.integration.journal.Outcome;
import com.fleetpilot.integration.journal.Outcome.Issue;
import com.fleetpilot.integration.route.Csv;
import com.fleetpilot.integration.route.fuelcard.FuelTransactions.FuelTransaction;
import com.fleetpilot.integration.route.fuelcard.FuelTransactions.LastFill;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.springframework.stereotype.Component;

/**
 * Checks the transactions of a fuel card provider against the fleet: a fuel card is a means of payment, and its
 * misuse (fuel for a private car, two fills in an hour, a jerrycan) costs fleets a few percent of their fuel budget.
 *
 * <ul>
 *   <li>UNKNOWN_VEHICLE: the registration is not in the fleet;</li>
 *   <li>WRONG_FUEL: a product the vehicle cannot use (diesel for a petrol car, any fuel for an electric car);</li>
 *   <li>OVER_CAPACITY: more litres than the tank of the category holds;</li>
 *   <li>TOO_FREQUENT: a second fill less than four hours after the previous one;</li>
 *   <li>ODOMETER: an odometer lower than at the previous fill.</li>
 * </ul>
 * Every transaction is stored, with its anomalies; one already received (same provider and reference) is skipped.
 */
@Component
public class FuelCardChecker {

    static final Duration TOO_CLOSE = Duration.ofHours(4);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Map<String, Integer> TANKS = Map.of("CAR", 60, "VAN", 80, "TRUCK", 600, "MACHINE", 200);
    private static final Map<String, String> PRODUCTS = Map.of("GAZOLE", "gazole", "SP95-E10", "SP95-E10",
            "SP98", "SP98", "GPL", "GPL", "GNV", "GNV", "ADBLUE", "AdBlue");

    private final FleetDirectory fleet;
    private final FuelTransactions store;

    public FuelCardChecker(FleetDirectory fleet, FuelTransactions store) {
        this.fleet = fleet;
        this.store = store;
    }

    /** Exchange property: the transactions stored, published on Kafka once the file is journaled. */
    public static final String STORED = "fleetpilotFuelStored";

    public Outcome check(List<Map<String, String>> rows, @ExchangeProperty(FileIntake.JOURNAL_ID) UUID exchangeId,
            Exchange exchange) {
        if (rows.isEmpty()) {
            return Outcome.rejected(null, Issue.error(null, null, "EMPTY_FILE", "Le fichier ne contient aucune ligne"),
                    "Fichier vide");
        }
        String provider = Csv.optional(rows.getFirst(), "fournisseur");
        Map<String, FleetVehicle> vehicles = new HashMap<>();
        fleet.vehicles().forEach(v -> vehicles.put(v.registration(), v));

        List<Parsed> parsed = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            try {
                parsed.add(Parsed.of(i + 2, rows.get(i)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                issues.add(Issue.error(i + 2, rows.get(i).get("reference"), "UNREADABLE_LINE",
                        "Ligne illisible : " + e.getMessage()));
            }
        }
        // Checked in time order, so that "previous fill" means the previous one in time
        parsed.sort(Comparator.comparing(Parsed::at));
        Set<String> known = store.knownReferences(provider, parsed.stream().map(Parsed::reference).toList());
        Map<String, LastFill> last = new HashMap<>(store.lastFills(parsed.stream().map(Parsed::registration).distinct()
                .toList(), parsed.isEmpty() ? Instant.now() : parsed.getFirst().at()));

        List<FuelTransaction> stored = new ArrayList<>();
        int clean = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (Parsed p : parsed) {
            if (!known.add(p.reference())) {
                issues.add(Issue.error(p.line(), p.reference(), "DUPLICATE_TRANSACTION",
                        "Transaction " + p.reference() + " déjà reçue : ignorée"));
                continue;
            }
            FleetVehicle vehicle = vehicles.get(p.registration());
            Set<String> anomalies = new LinkedHashSet<>();
            List<Issue> found = anomalies(p, vehicle, last.get(p.registration()), anomalies);
            issues.addAll(found);
            if (anomalies.isEmpty()) {
                clean++;
            }
            amount = amount.add(p.amount());
            stored.add(new FuelTransaction(UUID.randomUUID(), exchangeId, provider, p.reference(), p.card(), p.at(),
                    p.station(), p.city(), p.product(), p.litres(), p.unitPrice(), p.amount(), p.odometer(),
                    p.registration(), vehicle == null ? null : vehicle.id(), vehicle == null ? null : vehicle.companyId(),
                    vehicle == null ? null : vehicle.energy(), anomalies.isEmpty() ? null : String.join(",", anomalies)));
            if (!"ADBLUE".equals(p.product())) {
                // a can of AdBlue is not a fill: the next fill is compared with the previous fuel fill
                last.put(p.registration(), new LastFill(p.at(), p.odometer()));
            }
        }
        store.save(stored);
        exchange.setProperty(STORED, stored);
        long withAnomaly = stored.stream().filter(t -> t.anomalies() != null).count();
        String summary = "%s : %d transactions, %s TTC".formatted(provider, stored.size(), money(amount))
                + (withAnomaly == 0 ? ", aucune anomalie" : ", %d en anomalie".formatted(withAnomaly));
        return new Outcome(provider, rows.size(), clean, issues, summary, null);
    }

    private static List<Issue> anomalies(Parsed p, FleetVehicle vehicle, LastFill previous, Set<String> codes) {
        List<Issue> issues = new ArrayList<>();
        if (vehicle == null) {
            codes.add("UNKNOWN_VEHICLE");
            issues.add(Issue.warning(p.line(), p.reference(), "UNKNOWN_VEHICLE",
                    "Véhicule %s inconnu du parc (carte %s)".formatted(p.registration(), masked(p.card()))));
            return issues;
        }
        if (!compatible(p.product(), vehicle.energy())) {
            codes.add("WRONG_FUEL");
            issues.add(Issue.warning(p.line(), p.reference(), "WRONG_FUEL", "%s acheté pour %s %s (%s)".formatted(
                    PRODUCTS.getOrDefault(p.product(), p.product()), vehicle.make(), vehicle.model(),
                    energyLabel(vehicle.energy()))));
        }
        int tank = TANKS.getOrDefault(vehicle.category(), 60);
        if (!"ADBLUE".equals(p.product()) && p.litres().intValue() > tank) {
            codes.add("OVER_CAPACITY");
            issues.add(Issue.warning(p.line(), p.reference(), "OVER_CAPACITY",
                    "%s L pour un réservoir d'environ %d L".formatted(p.litres().stripTrailingZeros().toPlainString(),
                            tank)));
        }
        if (previous != null && !"ADBLUE".equals(p.product())
                && Duration.between(previous.at(), p.at()).compareTo(TOO_CLOSE) < 0) {
            codes.add("TOO_FREQUENT");
            issues.add(Issue.warning(p.line(), p.reference(), "TOO_FREQUENT", "Deuxième plein en %d min pour %s"
                    .formatted(Duration.between(previous.at(), p.at()).toMinutes(), p.registration())));
        }
        if (previous != null && previous.odometer() != null && p.odometer() != null
                && p.odometer() < previous.odometer()) {
            codes.add("ODOMETER");
            issues.add(Issue.warning(p.line(), p.reference(), "ODOMETER",
                    "Kilométrage saisi %d km, inférieur au plein précédent (%d km)".formatted(p.odometer(),
                            previous.odometer())));
        }
        return issues;
    }

    static boolean compatible(String product, String energy) {
        return switch (energy == null ? "" : energy) {
            case "DIESEL" -> product.equals("GAZOLE") || product.equals("ADBLUE");
            case "PETROL", "HYBRID", "PLUG_IN_HYBRID" -> product.startsWith("SP");
            case "LPG" -> product.equals("GPL") || product.startsWith("SP");
            case "CNG" -> product.equals("GNV") || product.startsWith("SP");
            default -> false;
        };
    }

    private static String energyLabel(String energy) {
        return switch (energy) {
            case "ELECTRIC" -> "électrique";
            case "HYDROGEN" -> "hydrogène";
            case "DIESEL" -> "diesel";
            case "PETROL" -> "essence";
            case "HYBRID", "PLUG_IN_HYBRID" -> "hybride essence";
            case "LPG" -> "GPL";
            case "CNG" -> "GNV";
            default -> energy.toLowerCase(Locale.ROOT);
        };
    }

    static String masked(String card) {
        return card.length() <= 4 ? card : "…" + card.substring(card.length() - 4);
    }

    private static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(Locale.FRANCE).format(amount);
    }

    /** One transaction as read from the file. */
    record Parsed(int line, String reference, Instant at, String card, String registration, String station,
            String city, String product, BigDecimal litres, BigDecimal unitPrice, BigDecimal amount, Integer odometer) {

        static Parsed of(int line, Map<String, String> row) {
            String product = Csv.text(row, "produit").toUpperCase(Locale.ROOT);
            if (!PRODUCTS.containsKey(product)) {
                throw new IllegalArgumentException("produit « " + product + " » inconnu");
            }
            return new Parsed(line, Csv.text(row, "reference"),
                    LocalDateTime.parse(Csv.text(row, "date_heure"), WHEN).atZone(PARIS).toInstant(),
                    Csv.text(row, "carte"), Csv.text(row, "immatriculation").toUpperCase(Locale.ROOT),
                    Csv.text(row, "station"), Csv.optional(row, "ville"), product, Csv.decimal(row, "volume_l"),
                    Csv.decimal(row, "prix_litre_ttc"), Csv.decimal(row, "montant_ttc"), Csv.integer(row, "kilometrage"));
        }
    }
}
