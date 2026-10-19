package com.fleetpilot.integration.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Garage;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Operation;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Part;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Tariff;
import com.fleetpilot.integration.fleet.FleetDirectory.FleetVehicle;
import com.fleetpilot.integration.fleet.FleetDirectory.LeaseContract;

/**
 * The files the partners would have sent over the last weeks, built from the real demo fleet so that they match it:
 * the same registrations, contracts, garages and tariff. Deterministic (fixed seed). Each set contains the mistakes a
 * real integration meets, so that the journal shows every outcome:
 *
 * <ul>
 *   <li>garages: compliant quotes, an overpriced one, a vehicle unknown to the fleet, a wrong total, a file breaking
 *       its schema, the same file sent twice;</li>
 *   <li>lessors: an invoice conforming to the contracts, and one with overbilled rents, a rent after the end of a
 *       contract, an unknown contract and a rent billed twice;</li>
 *   <li>fuel cards: eight weeks of fills, with a few wrong fuels, jerrycans, fills too close, an odometer going
 *       backwards and cards used for vehicles outside the fleet.</li>
 * </ul>
 */
public final class PartnerFiles {

    /** A file to drop in a folder of the inbox. */
    public record DemoFile(String folder, String name, String content) {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** Stations by pairs in the same area: a vehicle fills up around its base. */
    private static final String[][] STATIONS = {
        {"Relais Marseille Prado", "Marseille"}, {"Station Aubagne Napollon", "Aubagne"},
        {"Relais Aix Les Milles", "Aix-en-Provence"}, {"Station Vitrolles A7", "Vitrolles"},
        {"Relais Lyon Gerland", "Lyon"}, {"Station Villeurbanne Croix-Luizet", "Villeurbanne"},
        {"Relais Paris Porte d'Orléans", "Paris"}, {"Station Nanterre Préfecture", "Nanterre"},
        {"Relais Bordeaux Lac", "Bordeaux"}, {"Station Mérignac Aéroport", "Mérignac"},
        {"Relais Toulouse Purpan", "Toulouse"}, {"Station Blagnac Aéroport", "Blagnac"},
        {"Aire de Lançon-Provence A7", "Lançon-Provence"}, {"Aire de Montélimar A7", "Montélimar"}};
    private static final Map<String, BigDecimal> PRICES = Map.of("GAZOLE", new BigDecimal("1.719"),
            "SP95-E10", new BigDecimal("1.809"), "SP98", new BigDecimal("1.919"), "GPL", new BigDecimal("0.999"),
            "GNV", new BigDecimal("1.459"), "ADBLUE", new BigDecimal("0.899"));

    private final LocalDate today;
    private final Random random = new Random(20_261_015L);

    public PartnerFiles(LocalDate today) {
        this.today = today;
    }

    // ------------------------------------------------------------------------------------------------- garages

    public List<DemoFile> garageQuotes(List<FleetVehicle> fleet, Map<String, Garage> garages, Tariff tariff) {
        List<FleetVehicle> cars = fleet.stream().filter(v -> "CAR".equals(v.category()) && "ACTIVE".equals(v.status()))
                .sorted(Comparator.comparing(FleetVehicle::registration)).toList();
        List<FleetVehicle> vans = fleet.stream().filter(v -> "VAN".equals(v.category()) && "ACTIVE".equals(v.status()))
                .sorted(Comparator.comparing(FleetVehicle::registration)).toList();
        Map<String, Part> parts = new LinkedHashMap<>();
        tariff.parts().forEach(p -> parts.put(p.code(), p));
        Map<String, Operation> operations = new LinkedHashMap<>();
        tariff.operations().forEach(o -> operations.put(o.code(), o));
        LocalDate day = today.minusDays(6);
        List<DemoFile> files = new ArrayList<>();

        Garage prado = garages.get("MRS-01");
        FleetVehicle brakes = cars.get(cars.size() / 3);
        String brakesQuote = quote(prado, "PRD-" + day.getYear() + "-1042", day, brakes,
                "Remplacement des disques et plaquettes avant, bruit au freinage signalé par le conducteur",
                List.of(part(parts.get("DISQ-AV"), 1, 1.00), part(parts.get("PLAQ-AV"), 1, 1.00),
                        labour(operations.get("FREIN-AV-DISQ"), prado), supplies("Nettoyant freins et graisse", "8.50")));
        files.add(new DemoFile("garages", day + "_garage-du-prado_PRD-1042.xml", brakesQuote));

        Garage vieuxPort = garages.get("MRS-02");
        FleetVehicle bumper = vans.get(vans.size() / 4);
        files.add(new DemoFile("garages", day.plusDays(1) + "_carrosserie-vieux-port_CVP-3318.xml",
                quote(vieuxPort, "CVP-" + day.getYear() + "-3318", day.plusDays(1), bumper,
                        "Choc arrière sur parking client : remplacement du pare-chocs et du feu arrière gauche",
                        List.of(new Line("PIECE", null, "Pare-chocs arrière nu, à peindre", "1", "286.00"),
                                new Line("PIECE", null, "Feu arrière gauche complet", "1", "148.40"),
                                labour(operations.get("PARE-CHOC"), vieuxPort),
                                labour(operations.get("PEINTURE"), vieuxPort),
                                supplies("Ingrédients peinture", "64.00")))));

        Garage partDieu = garages.get("LYS-02");
        FleetVehicle service = cars.get(cars.size() / 2);
        files.add(new DemoFile("garages", day.plusDays(2) + "_garage-part-dieu_GPD-0788.xml",
                quote(partDieu, "GPD-" + day.getYear() + "-0788", day.plusDays(2), service,
                        "Révision des 60 000 km selon le plan du constructeur",
                        List.of(part(parts.get("FILT-HUILE"), 1, 1.65), part(parts.get("HUILE-5W30"), 5, 1.40),
                                part(parts.get("FILT-AIR"), 1, 1.0), part(parts.get("FILT-HAB"), 1, 1.0),
                                labour(operations.get("REVISION"), partDieu), supplies("Produits d'atelier", "12.00")))));

        Garage chevalier = garages.get("PAR-01");
        FleetVehicle unknown = new FleetVehicle(null, null, "FR-404-KZ", "CAR", "Peugeot", "308", "DIESEL", "ACTIVE",
                54_210);
        files.add(new DemoFile("garages", day.plusDays(3) + "_garage-chevalier_GCH-2207.xml",
                quote(chevalier, "GCH-" + day.getYear() + "-2207", day.plusDays(3), unknown,
                        "Remplacement de la batterie, démarrages difficiles",
                        List.of(part(parts.get("BATT-12V"), 1, 1.0), labour(operations.get("BATTERIE"), chevalier)))));

        Garage lefevre = garages.get("LYS-06");
        FleetVehicle clutch = vans.get(vans.size() / 2);
        String wrongTotal = quote(lefevre, "GLF-" + day.getYear() + "-0415", day.plusDays(4), clutch,
                "Remplacement de l'embrayage complet",
                List.of(part(parts.get("KIT-EMBR"), 1, 1.0), labour(operations.get("EMBRAY"), lefevre)));
        files.add(new DemoFile("garages", day.plusDays(4) + "_garage-lefevre_GLF-0415.xml",
                wrongTotal.replaceFirst("<totalHT>[0-9.]+</totalHT>", "<totalHT>1180.00</totalHT>")));

        FleetVehicle tyres = cars.get(cars.size() / 5);
        String invalid = quote(chevalier, "GCH-" + day.getYear() + "-2231", day.plusDays(5), tyres,
                "Remplacement de deux pneumatiques avant",
                List.of(part(parts.get("PNEU"), 2, 1.0), labour(operations.get("PNEU-MONT-2"), chevalier)));
        files.add(new DemoFile("garages", day.plusDays(5) + "_garage-chevalier_GCH-2231.xml",
                invalid.replace("immatriculation=\"" + tyres.registration() + "\"",
                        "immatriculation=\"" + tyres.registration().replace("-", "").toLowerCase(Locale.ROOT) + "\"")));

        // The software of the Garage du Prado sends the same quote again after a network error
        files.add(new DemoFile("garages", day.plusDays(5) + "_garage-du-prado_PRD-1042_renvoi.xml", brakesQuote));
        return files;
    }

    record Line(String nature, String code, String label, String quantity, String unitPrice) {

        BigDecimal amount() {
            return new BigDecimal(quantity).multiply(new BigDecimal(unitPrice)).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static Line part(Part part, int quantity, double priceFactor) {
        BigDecimal price = part.priceCar().multiply(BigDecimal.valueOf(priceFactor)).setScale(2, RoundingMode.HALF_UP);
        return new Line("PIECE", part.code(), part.label(), String.valueOf(quantity), price.toPlainString());
    }

    private static Line labour(Operation operation, Garage garage) {
        return new Line("MAIN_OEUVRE", operation.code(), operation.label(), operation.hoursCar().toPlainString(),
                garage.hourlyRate().setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private static Line supplies(String label, String amount) {
        return new Line("INGREDIENTS", null, label, "1", amount);
    }

    private static String quote(Garage garage, String reference, LocalDate day, FleetVehicle vehicle, String subject,
            List<Line> lines) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <devis xmlns="urn:fleetpilot:devis-garage:1" version="1.0">
                """);
        xml.append("  <emetteur siret=\"").append(garage.siret()).append("\">").append(escape(garage.name()))
                .append("</emetteur>\n");
        xml.append("  <reference>").append(reference).append("</reference>\n");
        xml.append("  <dateEmission>").append(day).append("</dateEmission>\n");
        xml.append("  <vehicule immatriculation=\"").append(vehicle.registration()).append("\" kilometrage=\"")
                .append(vehicle.mileage()).append("\"/>\n");
        xml.append("  <objet>").append(escape(subject)).append("</objet>\n");
        xml.append("  <lignes>\n");
        BigDecimal total = BigDecimal.ZERO;
        for (Line line : lines) {
            xml.append("    <ligne nature=\"").append(line.nature()).append('"');
            if (line.code() != null) {
                xml.append(" code=\"").append(line.code()).append('"');
            }
            xml.append(">\n      <libelle>").append(escape(line.label())).append("</libelle>\n");
            xml.append("      <quantite>").append(line.quantity()).append("</quantite>\n");
            xml.append("      <prixUnitaireHT>").append(line.unitPrice()).append("</prixUnitaireHT>\n");
            xml.append("    </ligne>\n");
            total = total.add(line.amount());
        }
        xml.append("  </lignes>\n");
        xml.append("  <totalHT>").append(total.toPlainString()).append("</totalHT>\n");
        xml.append("</devis>\n");
        return xml.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------------------------------------------- lessors

    /** The invoice of last month of a lessor; with {@code mistakes}, the lessor bills what it should not. */
    public DemoFile lessorInvoice(String lessor, String prefix, List<LeaseContract> contracts, boolean mistakes) {
        YearMonth period = YearMonth.from(today).minusMonths(1);
        String invoice = "%s-F-%s-%04d".formatted(prefix, period.toString().replace("-", ""), 300 + random.nextInt(600));
        List<LeaseContract> billed = contracts.stream()
                .filter(c -> !c.startDate().isAfter(period.atEndOfMonth()))
                .filter(c -> !(c.endedOn() != null ? c.endedOn() : c.endDate()).isBefore(period.atDay(1)))
                .sorted(Comparator.comparing(LeaseContract::contractNumber)).toList();
        StringBuilder csv = new StringBuilder(
                "loueur;numero_facture;date_facture;numero_contrat;immatriculation;periode;loyer_ht;prestations_ht;total_ht\n");
        LocalDate issued = period.plusMonths(1).atDay(3);
        for (int i = 0; i < billed.size(); i++) {
            LeaseContract c = billed.get(i);
            BigDecimal rent = c.monthlyRent();
            if (mistakes && i % 61 == 17) {
                rent = rent.multiply(new BigDecimal("1.0" + (3 + i % 5))).setScale(2, RoundingMode.HALF_UP);
            }
            csv.append(row(lessor, invoice, issued, c.contractNumber(), c.registration(), period, rent, services(c)));
        }
        if (mistakes) {
            // a contract returned before the period, still billed
            contracts.stream().filter(c -> c.endedOn() != null && c.endedOn().isBefore(period.atDay(1)))
                    .min(Comparator.comparing(LeaseContract::contractNumber))
                    .ifPresent(c -> csv.append(row(lessor, invoice, issued, c.contractNumber(), c.registration(), period,
                            c.monthlyRent(), services(c))));
            // a contract FleetPilot does not know
            csv.append(row(lessor, invoice, issued, prefix + "-2024-199871", "GA-731-PL", period,
                    new BigDecimal("512.40"), new BigDecimal("38.00")));
            // the same rent billed twice
            LeaseContract twice = billed.get(billed.size() / 2);
            csv.append(row(lessor, invoice, issued, twice.contractNumber(), twice.registration(), period,
                    twice.monthlyRent(), services(twice)));
        }
        return new DemoFile("lessors", "%s_factures_%s.csv".formatted(prefix.toLowerCase(Locale.ROOT), period), csv.toString());
    }

    private static BigDecimal services(LeaseContract c) {
        return c.monthlyRent().multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
    }

    private static String row(String lessor, String invoice, LocalDate issued, String contract, String registration,
            YearMonth period, BigDecimal rent, BigDecimal services) {
        return String.join(";", lessor, invoice, issued.toString(), contract, registration, period.toString(),
                french(rent), french(services), french(rent.add(services))) + "\n";
    }

    private static String french(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    // ------------------------------------------------------------------------------------------------ fuel cards

    /**
     * Fuel card files of one provider, one file per fortnight over the last eight weeks.
     *
     * @param trucksOnly the motorway network for heavy vehicles (fills with AdBlue), or the general network
     */
    public List<DemoFile> fuelCards(String provider, String prefix, List<FleetVehicle> fleet, boolean trucksOnly) {
        Predicate<FleetVehicle> eligible = trucksOnly
                ? v -> "TRUCK".equals(v.category()) && "DIESEL".equals(v.energy())
                : v -> !"TRUCK".equals(v.category()) && !"ELECTRIC".equals(v.energy()) && !"HYDROGEN".equals(v.energy());
        List<FleetVehicle> vehicles = fleet.stream().filter(v -> "ACTIVE".equals(v.status())).filter(eligible)
                .sorted(Comparator.comparing(FleetVehicle::registration)).toList();
        List<FleetVehicle> carded = new ArrayList<>();
        for (int i = 0; i < vehicles.size(); i++) {
            if (trucksOnly || i % 3 != 2) {
                carded.add(vehicles.get(i));
            }
        }
        List<FleetVehicle> electric = fleet.stream().filter(v -> "ELECTRIC".equals(v.energy()) && "CAR".equals(v.category()))
                .sorted(Comparator.comparing(FleetVehicle::registration)).toList();
        List<FleetVehicle> petrol = carded.stream().filter(v -> "PETROL".equals(v.energy())).toList();
        List<FleetVehicle> diesel = carded.stream().filter(v -> "DIESEL".equals(v.energy())).toList();

        List<DemoFile> files = new ArrayList<>();
        LocalDate end = today.minusDays(1);
        int sequence = 1;
        // the odometer of each vehicle runs on from one fortnight to the next, up to its mileage today
        Map<String, Integer> odometers = new LinkedHashMap<>();
        carded.forEach(v -> odometers.put(v.registration(), Math.max(1_000, v.mileage() - (trucksOnly ? 7_000 : 4_800))));
        for (int fortnight = 3; fortnight >= 0; fortnight--) {
            LocalDate to = end.minusDays(14L * fortnight);
            LocalDate from = to.minusDays(13);
            List<String[]> lines = new ArrayList<>();
            for (FleetVehicle v : carded) {
                int odometer = odometers.get(v.registration());
                int fills = trucksOnly ? 2 + random.nextInt(2) : 1 + random.nextInt(3);
                LocalDate day = from.plusDays(random.nextInt(4));
                for (int f = 0; f < fills && !day.isAfter(to); f++) {
                    odometer += trucksOnly ? 520 + random.nextInt(380) : 380 + random.nextInt(320);
                    lines.add(fill(v, product(v), litres(v), day, odometer, null));
                    if (trucksOnly && random.nextInt(4) == 0) {
                        lines.add(fill(v, "ADBLUE", new BigDecimal(20 + random.nextInt(25)), day, odometer, null));
                    }
                    day = day.plusDays(trucksOnly ? 3 + random.nextInt(3) : 4 + random.nextInt(4));
                }
                odometers.put(v.registration(), odometer);
            }
            if (!trucksOnly && fortnight == 0) {
                injectAnomalies(lines, carded, petrol, diesel, electric, from, odometers);
            }
            lines.sort(Comparator.comparing(l -> l[1]));
            StringBuilder csv = new StringBuilder("fournisseur;reference;date_heure;carte;immatriculation;station;ville;"
                    + "produit;volume_l;prix_litre_ttc;montant_ttc;kilometrage\n");
            for (String[] l : lines) {
                l[0] = "%s-%s-%06d".formatted(prefix, from.toString().replace("-", ""), sequence++);
                BigDecimal litres = new BigDecimal(l[7]);
                BigDecimal price = PRICES.get(l[6]).add(BigDecimal.valueOf(random.nextInt(9) - 4, 2));
                csv.append(String.join(";", provider, l[0], l[1], l[2], l[3], l[4], l[5], l[6],
                        french(litres), price.toPlainString().replace('.', ','),
                        french(litres.multiply(price)), l[8])).append('\n');
            }
            files.add(new DemoFile("fuelcards", "%s_transactions_%s_%s.csv".formatted(prefix.toLowerCase(Locale.ROOT),
                    from, to), csv.toString()));
        }
        return files;
    }

    private void injectAnomalies(List<String[]> lines, List<FleetVehicle> carded, List<FleetVehicle> petrol,
            List<FleetVehicle> diesel, List<FleetVehicle> electric, LocalDate from, Map<String, Integer> odometers) {
        Function<FleetVehicle, Integer> next = v -> odometers.getOrDefault(v.registration(), v.mileage()) + 60;
        LocalDate day = from.plusDays(9);
        // diesel in a petrol car, petrol in a diesel van, and fuel bought with the card of an electric car
        lines.add(fill(petrol.get(3), "GAZOLE", new BigDecimal("41.20"), day, next.apply(petrol.get(3)), "08:12"));
        lines.add(fill(diesel.get(11), "SP95-E10", new BigDecimal("38.65"), day.plusDays(1),
                next.apply(diesel.get(11)), "17:46"));
        lines.add(fill(electric.get(7), "SP98", new BigDecimal("44.10"), day.plusDays(2), electric.get(7).mileage(),
                "12:03"));
        // jerrycans: more than the tank holds
        lines.add(fill(carded.get(21), "GAZOLE", new BigDecimal("86.40"), day.plusDays(1), next.apply(carded.get(21)),
                "19:22"));
        lines.add(fill(carded.get(58), product(carded.get(58)), new BigDecimal("97.80"), day.plusDays(3),
                next.apply(carded.get(58)), "07:55"));
        // two fills an hour apart
        FleetVehicle twice = carded.get(33);
        lines.add(fill(twice, product(twice), new BigDecimal("39.80"), day.plusDays(2), next.apply(twice), "10:05"));
        lines.add(fill(twice, product(twice), new BigDecimal("37.15"), day.plusDays(2), next.apply(twice) + 8, "11:12"));
        // odometer typed wrong at the pump
        FleetVehicle typo = carded.get(44);
        lines.add(fill(typo, product(typo), new BigDecimal("42.00"), day.plusDays(4), typo.mileage() / 10, "16:40"));
        // cards used for vehicles that are not in the fleet
        lines.add(fill(new FleetVehicle(null, null, "GK-118-TR", "CAR", null, null, "DIESEL", "ACTIVE", 61_000),
                "GAZOLE", new BigDecimal("46.30"), day.plusDays(3), 61_220, "13:31"));
        lines.add(fill(new FleetVehicle(null, null, "FZ-902-AH", "CAR", null, null, "PETROL", "ACTIVE", 23_000),
                "SP98", new BigDecimal("39.95"), day.plusDays(4), 23_410, "09:48"));
    }

    private String[] fill(FleetVehicle v, String product, BigDecimal litres, LocalDate day, int odometer, String time) {
        LocalDate onDay = day.getDayOfWeek() == DayOfWeek.SUNDAY && time == null ? day.plusDays(1) : day;
        String at = time != null ? onDay + " " + time
                : LocalDateTime.of(onDay, LocalTime.of(6 + random.nextInt(13), random.nextInt(60))).format(STAMP);
        int area = Math.floorMod(v.registration().hashCode(), STATIONS.length / 2);
        String[] station = STATIONS[area * 2 + random.nextInt(2)];
        String card = "7089%012d".formatted(Math.floorMod((long) v.registration().hashCode() * 7919L, 1_000_000_000_000L));
        return new String[] {null, at, card, v.registration(), station[0], station[1], product,
            litres.setScale(2, RoundingMode.HALF_UP).toPlainString(), String.valueOf(odometer)};
    }

    private static String product(FleetVehicle v) {
        return switch (v.energy()) {
            case "DIESEL" -> "GAZOLE";
            case "LPG" -> "GPL";
            case "CNG" -> "GNV";
            default -> "SP95-E10";
        };
    }

    private BigDecimal litres(FleetVehicle v) {
        BigDecimal whole = switch (v.category()) {
            case "TRUCK" -> BigDecimal.valueOf(150 + random.nextInt(140));
            case "VAN" -> BigDecimal.valueOf(45 + random.nextInt(30));
            case "MACHINE" -> BigDecimal.valueOf(60 + random.nextInt(90));
            default -> BigDecimal.valueOf(30 + random.nextInt(24));
        };
        return whole.add(BigDecimal.valueOf(random.nextInt(100), 2));
    }
}
