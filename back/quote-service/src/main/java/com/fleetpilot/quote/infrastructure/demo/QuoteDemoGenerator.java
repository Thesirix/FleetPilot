package com.fleetpilot.quote.infrastructure.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.quote.domain.analysis.QuoteAnalysis;
import com.fleetpilot.quote.domain.analysis.QuoteAnalyzer;
import com.fleetpilot.quote.domain.analysis.QuoteLineInput;
import com.fleetpilot.quote.domain.analysis.Tariff;
import com.fleetpilot.quote.domain.model.Garage;
import com.fleetpilot.quote.domain.model.GarageNetwork;
import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.LineKind;
import com.fleetpilot.quote.domain.model.Quote;
import com.fleetpilot.quote.domain.model.QuoteChannel;
import com.fleetpilot.quote.domain.model.QuoteStatus;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.TariffClass;
import com.fleetpilot.quote.domain.model.VehicleRef;
import com.fleetpilot.quote.infrastructure.demo.QuoteDemoCatalog.Applies;
import com.fleetpilot.quote.infrastructure.demo.QuoteDemoCatalog.LineSpec;
import com.fleetpilot.quote.infrastructure.demo.QuoteDemoCatalog.Scenario;
import com.fleetpilot.quote.infrastructure.demo.QuoteDemoCatalog.Town;
import com.fleetpilot.quote.infrastructure.demo.QuoteDemoCatalog.Workshop;

/**
 * Generates the garage network and a year of garage quotes on the vehicles of the demo fleet.
 *
 * <p>Deterministic: the same seed, reference instant and vehicles always give the same data. Every quote goes through
 * the real domain (analysis, negotiation, decision, invoice), so the demo shows exactly what the rules produce. Each
 * garage has a pricing behaviour: most are fair, some are loose with the tariff, a few overcharge heavily.
 */
final class QuoteDemoGenerator {

    static final long DEFAULT_SEED = 20_261_015L;
    /** The two garages that have a demo account in Keycloak. */
    static final String FEATURED_GARAGE = "MRS-01";
    static final String FEATURED_BODY_SHOP = "MRS-02";

    private static final int HISTORY_QUOTES = 690;
    private static final int RECENT_QUOTES = 60;
    private static final int UNIDENTIFIED_QUOTES = 5;
    private static final BigDecimal AUTO_APPROVAL_LIMIT = new BigDecimal("300");
    private static final BigDecimal MANAGER_LIMIT = new BigDecimal("5000");
    private static final List<String> MANAGERS = List.of("claire.morel", "yanis.cherif", "sophie.garnier");
    private static final String ADMIN = "theo.lambert";
    private static final String[] STREETS = {"avenue Jean Jaurès", "rue Victor Hugo", "boulevard Gambetta",
        "avenue de la République", "rue Pasteur", "avenue du Général de Gaulle", "rue de la Gare",
        "chemin des Prés", "avenue des Lilas", "rue de l'Industrie"};

    private enum Behaviour {
        FAIR,
        LOOSE,
        HEAVY
    }

    private record GarageInfo(Garage garage, Behaviour behaviour) {
    }

    /** What the generator produced: garages first, then quotes ready to be saved. */
    record Result(List<Garage> garages, List<Quote> quotes) {
    }

    private final Random random;
    private final Instant now;
    private final ZoneId zone;
    private final Tariff tariff;
    private final QuoteAnalyzer analyzer;
    private final List<GarageInfo> garages = new ArrayList<>();
    private final Map<Region, Map<GarageNetwork, List<GarageInfo>>> byRegion = new EnumMap<>(Region.class);
    private final Map<String, GarageInfo> byCode = new HashMap<>();
    private final Map<UUID, Integer> garageSequences = new HashMap<>();
    private final Set<String> sirets = new HashSet<>();
    private int invoiceSequence;

    QuoteDemoGenerator(long seed, Instant now, ZoneId zone, Tariff tariff) {
        this.random = new Random(seed);
        this.now = now;
        this.zone = zone;
        this.tariff = tariff;
        this.analyzer = new QuoteAnalyzer(tariff);
    }

    static UUID uuid(String kind, String key) {
        return UUID.nameUUIDFromBytes(("fleetpilot-demo:" + kind + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    Result generate(List<VehicleRef> vehicles) {
        for (Town town : QuoteDemoCatalog.TOWNS) {
            for (int i = 0; i < town.garages(); i++) {
                addGarage(town, i);
            }
        }
        List<VehicleRef> sorted = vehicles.stream().sorted(Comparator.comparing(VehicleRef::getRegistration)).toList();
        List<Quote> quotes = new ArrayList<>();
        if (!sorted.isEmpty()) {
            List<Instant> slots = new ArrayList<>();
            for (int i = 0; i < HISTORY_QUOTES; i++) {
                slots.add(slot(365));
            }
            for (int i = 0; i < RECENT_QUOTES; i++) {
                slots.add(slot(12));
            }
            slots.sort(Comparator.naturalOrder());
            Set<Integer> unidentified = new HashSet<>();
            while (unidentified.size() < UNIDENTIFIED_QUOTES) {
                unidentified.add(slots.size() - 1 - random.nextInt(40));
            }
            for (int i = 0; i < slots.size(); i++) {
                quotes.add(quote(i + 1, slots.get(i), sorted.get(random.nextInt(sorted.size())), unidentified.contains(i)));
            }
        }
        return new Result(garages.stream().map(GarageInfo::garage).toList(), quotes);
    }

    // ---------------------------------------------------------------- garages

    private void addGarage(Town town, int index) {
        String code = "%s-%02d".formatted(town.code(), index + 1);
        Region region = Region.ofPostalCode(town.postalCode()).orElseThrow();
        GarageNetwork network = network(code);
        String place = town.places().get(index % town.places().size());
        String name = name(code, network, town, place);
        String brand = network == GarageNetwork.DEALER ? name.substring(0, name.indexOf(' '))
                : network == GarageNetwork.TRUCK && !name.startsWith("PL ") ? truckBrand(name) : null;
        BigDecimal reference = tariff.hourlyRate(region, LabourCategory.MECH,
                network == GarageNetwork.TRUCK ? TariffClass.TRUCK : TariffClass.CAR).orElse(BigDecimal.valueOf(75));
        double factor = switch (network) {
            case DEALER -> between(1.10, 1.30);
            case INDEPENDENT -> between(0.86, 1.03);
            case FAST_FIT -> between(0.95, 1.10);
            case BODYWORK -> between(0.92, 1.06);
            case TRUCK -> between(0.95, 1.15);
        };
        Behaviour behaviour = behaviour(code, network);
        Garage garage = new Garage(uuid("garage", code), code, new Garage.Profile(name, network, brand, siret(),
                (1 + random.nextInt(180)) + " " + STREETS[random.nextInt(STREETS.length)], town.postalCode(), town.city(),
                decimal(town.latitude() + between(-0.03, 0.03), 6), decimal(town.longitude() + between(-0.04, 0.04), 6),
                email(code, name), phone(region), decimal(reference.doubleValue() * factor, 0).setScale(2)));
        boolean approved = behaviour != Behaviour.HEAVY ? random.nextDouble() < 0.9 : random.nextDouble() < 0.55;
        garage.setApproved(approved, LocalDate.ofInstant(now, zone).minusDays(200 + random.nextInt(2400)));
        double rating = switch (behaviour) {
            case FAIR -> between(3.8, 5.0);
            case LOOSE -> between(3.0, 4.4);
            case HEAVY -> between(2.2, 3.5);
        };
        garage.rate(decimal(rating, 1));
        GarageInfo info = new GarageInfo(garage, behaviour);
        garages.add(info);
        byCode.put(code, info);
        byRegion.computeIfAbsent(region, r -> new EnumMap<>(GarageNetwork.class))
                .computeIfAbsent(network, n -> new ArrayList<>()).add(info);
    }

    private GarageNetwork network(String code) {
        if (code.equals(FEATURED_GARAGE)) {
            return GarageNetwork.INDEPENDENT;
        }
        if (code.equals(FEATURED_BODY_SHOP)) {
            return GarageNetwork.BODYWORK;
        }
        double draw = random.nextDouble();
        return draw < 0.25 ? GarageNetwork.DEALER : draw < 0.55 ? GarageNetwork.INDEPENDENT
                : draw < 0.75 ? GarageNetwork.FAST_FIT : draw < 0.90 ? GarageNetwork.BODYWORK : GarageNetwork.TRUCK;
    }

    private Behaviour behaviour(String code, GarageNetwork network) {
        if (code.equals(FEATURED_GARAGE)) {
            return Behaviour.LOOSE;
        }
        if (code.equals(FEATURED_BODY_SHOP)) {
            return Behaviour.FAIR;
        }
        double draw = random.nextDouble();
        double heavy = network == GarageNetwork.DEALER ? 0.18 : 0.13;
        double loose = network == GarageNetwork.DEALER ? 0.42 : 0.28;
        return draw < heavy ? Behaviour.HEAVY : draw < heavy + loose ? Behaviour.LOOSE : Behaviour.FAIR;
    }

    private String name(String code, GarageNetwork network, Town town, String place) {
        if (code.equals(FEATURED_GARAGE)) {
            return "Garage du Prado";
        }
        if (code.equals(FEATURED_BODY_SHOP)) {
            return "Carrosserie du Vieux-Port";
        }
        String surname = pick(QuoteDemoCatalog.SURNAMES);
        return switch (network) {
            case DEALER -> pick(QuoteDemoCatalog.CAR_BRANDS) + " " + town.city() + " " + place;
            case INDEPENDENT -> random.nextBoolean() ? "Garage " + surname + (random.nextBoolean() ? " et Fils" : "")
                    : "Garage " + place;
            case FAST_FIT -> switch (random.nextInt(3)) {
                case 0 -> "Auto Service " + place;
                case 1 -> "Point Pneus " + town.city();
                default -> "Centre Auto " + place;
            };
            case BODYWORK -> random.nextBoolean() ? "Carrosserie " + surname : "Carrosserie " + place;
            case TRUCK -> random.nextInt(3) == 0 ? "PL Services " + town.city()
                    : pick(QuoteDemoCatalog.TRUCK_BRANDS) + " " + town.city();
        };
    }

    private static String truckBrand(String name) {
        return QuoteDemoCatalog.TRUCK_BRANDS.stream().filter(name::startsWith).findFirst().orElse(null);
    }

    private String siret() {
        String siret;
        do {
            StringBuilder digits = new StringBuilder();
            for (int i = 0; i < 14; i++) {
                digits.append(random.nextInt(10));
            }
            siret = digits.toString();
        } while (!sirets.add(siret));
        return siret;
    }

    private static String email(String code, String name) {
        if (code.equals(FEATURED_GARAGE)) {
            return "atelier@garage-du-prado.fr";
        }
        if (code.equals(FEATURED_BODY_SHOP)) {
            return "devis@carrosserie-vieux-port.fr";
        }
        String slug = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "contact@" + slug + ".fr";
    }

    private String phone(Region region) {
        String prefix = switch (region) {
            case IDF -> "01";
            case HDF, GES -> "03";
            case PAC, ARA, OCC -> "04";
            case NAQ -> "05";
            case PDL -> "02";
        };
        return "%s %02d %02d %02d %02d".formatted(prefix, 10 + random.nextInt(90), random.nextInt(100),
                random.nextInt(100), random.nextInt(100));
    }

    // ---------------------------------------------------------------- quotes

    private Quote quote(int sequence, Instant submittedAt, VehicleRef vehicle, boolean unidentified) {
        TariffClass tariffClass = vehicle.tariffClass();
        Scenario scenario = scenario(vehicle);
        boolean recent = submittedAt.isAfter(now.minus(Duration.ofDays(12)));
        GarageInfo garage = garageFor(vehicle, scenario, recent);
        List<QuoteLineInput> lines = lines(scenario, tariffClass, garage);
        int garageNumber = garageSequences.merge(garage.garage().getId(), 1, Integer::sum);
        int year = LocalDate.ofInstant(submittedAt, zone).getYear();
        String reference = "DEV-%d-%06d".formatted(year, sequence);
        String registration = unidentified ? unknownRegistration() : vehicle.getRegistration();
        Quote quote = new Quote(uuid("quote", reference), reference, garage.garage(), unidentified ? null : vehicle,
                new Quote.Submission(registration, "D%02d-%04d".formatted(year % 100, garageNumber),
                        15_000 + random.nextInt(170_000), scenario.title(),
                        random.nextInt(4) == 0 ? QuoteChannel.ESB : QuoteChannel.PORTAL, lines),
                garage.garage().getEmail(), submittedAt);
        if (unidentified) {
            return quote;
        }
        QuoteAnalysis analysis = analyzer.analyze(quote.lineInputs(), tariffClass, garage.garage().getRegion());
        Instant analysedAt = submittedAt.plusSeconds(20 + random.nextInt(90));
        quote.applyAnalysis(analysis, AUTO_APPROVAL_LIMIT, analysedAt);
        live(quote, garage, analysedAt);
        return quote;
    }

    /** Plays the rest of the life of the quote, as far as its age allows. */
    private void live(Quote quote, GarageInfo garage, Instant analysedAt) {
        Instant decision = analysedAt.plus(hours(4, 52));
        String garageUser = garage.garage().getEmail();
        // the managers have not caught up with every quote of the last days: they form the work queue of the demo
        boolean backlog = analysedAt.isAfter(now.minus(Duration.ofDays(6))) && random.nextDouble() < 0.6;
        if (quote.getStatus().isOpen()) {
            if (decision.isAfter(now) || backlog) {
                Instant counter = analysedAt.plus(hours(1, 6));
                if (quote.getStatus() == QuoteStatus.TO_NEGOTIATE && counter.isBefore(now) && random.nextDouble() < 0.45) {
                    quote.proposeCounterOffer(counterAmount(quote), pick(QuoteDemoCatalog.COUNTER_MESSAGES), manager(quote),
                            counter);
                }
                return;
            }
            decide(quote, garageUser, decision);
        }
        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            return;
        }
        Instant base = quote.getDecidedAt();
        Instant done = base.plus(Duration.ofDays(1 + random.nextInt(9))).plus(hours(0, 8));
        if (done.isAfter(now)) {
            return;
        }
        quote.complete(LocalDate.ofInstant(done, zone), garageUser, done);
        Instant invoiced = done.plus(Duration.ofDays(random.nextInt(6))).plus(hours(1, 6));
        if (invoiced.isAfter(now)) {
            return;
        }
        double draw = random.nextDouble();
        double factor = draw < 0.86 ? 1.0 : draw < 0.92 ? between(1.003, 1.015) : draw < 0.98 ? between(1.04, 1.14)
                : between(0.97, 0.995);
        BigDecimal amount = quote.getAcceptedAmount().multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_UP);
        LocalDate day = LocalDate.ofInstant(invoiced, zone);
        quote.invoice("F%02d-%05d".formatted(day.getYear() % 100, ++invoiceSequence), amount, day, garageUser, invoiced);
    }

    private void decide(Quote quote, String garageUser, Instant at) {
        String manager = manager(quote);
        if (quote.getStatus() == QuoteStatus.TO_NEGOTIATE) {
            double draw = random.nextDouble();
            if (draw < 0.72) {
                quote.proposeCounterOffer(counterAmount(quote), pick(QuoteDemoCatalog.COUNTER_MESSAGES), manager, at);
                Instant answer = at.plus(hours(2, 40));
                if (answer.isAfter(now)) {
                    return;
                }
                if (random.nextDouble() < 0.8) {
                    quote.acceptCounterOffer(garageUser, answer);
                    return;
                }
                quote.refuseCounterOffer(pick(QuoteDemoCatalog.REFUSE_MESSAGES), garageUser, answer);
                Instant after = answer.plus(hours(2, 20));
                if (after.isAfter(now)) {
                    return;
                }
                if (random.nextBoolean()) {
                    quote.accept(pick(QuoteDemoCatalog.ACCEPT_COMMENTS), manager, after);
                } else {
                    quote.reject(pick(QuoteDemoCatalog.REJECT_REASONS), manager, after);
                }
            } else if (draw < 0.86) {
                quote.accept(pick(QuoteDemoCatalog.ACCEPT_COMMENTS), manager, at);
            } else {
                quote.reject(pick(QuoteDemoCatalog.REJECT_REASONS), manager, at);
            }
        } else if (random.nextDouble() < 0.88) {
            quote.accept(quote.getFindingCount() > 0 ? "Ligne hors tarif vérifiée avec le garage : conforme."
                    : "Conforme au tarif de référence.", manager, at);
        } else {
            quote.reject(pick(QuoteDemoCatalog.REJECT_REASONS), manager, at);
        }
    }

    /** Large quotes are decided by an administrator (double validation), the others by a fleet manager. */
    private String manager(Quote quote) {
        return quote.getAmount().compareTo(MANAGER_LIMIT) > 0 ? ADMIN : MANAGERS.get(random.nextInt(MANAGERS.size()));
    }

    /** The manager proposes the reference amount, rounded down to 5 EUR. */
    private static BigDecimal counterAmount(Quote quote) {
        BigDecimal target = quote.getReferenceAmount().min(quote.getAmount().multiply(new BigDecimal("0.97")));
        BigDecimal rounded = target.divide(BigDecimal.valueOf(5), 0, RoundingMode.FLOOR).multiply(BigDecimal.valueOf(5));
        return rounded.signum() > 0 ? rounded.setScale(2) : target.setScale(2, RoundingMode.HALF_UP);
    }

    private Scenario scenario(VehicleRef vehicle) {
        List<Scenario> candidates = QuoteDemoCatalog.SCENARIOS.stream().filter(s -> applies(s.applies(), vehicle)).toList();
        int total = candidates.stream().mapToInt(Scenario::weight).sum();
        int draw = random.nextInt(total);
        for (Scenario scenario : candidates) {
            draw -= scenario.weight();
            if (draw < 0) {
                return scenario;
            }
        }
        return candidates.getLast();
    }

    private static boolean applies(Applies applies, VehicleRef vehicle) {
        String energy = vehicle.getEnergy() == null ? "" : vehicle.getEnergy();
        boolean electric = energy.equals("ELECTRIC") || energy.equals("HYDROGEN");
        return switch (applies) {
            case ALL -> true;
            case COMBUSTION -> !electric;
            case DIESEL -> energy.equals("DIESEL");
            case ELECTRIC -> electric;
            case TRUCK -> vehicle.tariffClass() == TariffClass.TRUCK;
        };
    }

    /** Recent quotes go more often to the two garages with a demo account, so that their space has work to do. */
    private GarageInfo garageFor(VehicleRef vehicle, Scenario scenario, boolean recent) {
        // vehicles without a site are serviced around Marseille, where the three client companies are based
        Region region = vehicle.getCity() == null ? Region.PAC
                : QuoteDemoCatalog.SITE_REGIONS.getOrDefault(vehicle.getCity(), Region.PAC);
        boolean marseilleArea = region == Region.PAC;
        if (marseilleArea && scenario.workshop() == Workshop.BODYWORK && random.nextDouble() < (recent ? 0.5 : 0.22)) {
            return byCode.get(FEATURED_BODY_SHOP);
        }
        boolean truck = vehicle.tariffClass() == TariffClass.TRUCK;
        if (marseilleArea && !truck && scenario.workshop() == Workshop.MAINTENANCE
                && random.nextDouble() < (recent ? 0.35 : 0.12)) {
            return byCode.get(FEATURED_GARAGE);
        }
        List<GarageNetwork> networks = scenario.workshop() == Workshop.BODYWORK
                ? List.of(GarageNetwork.BODYWORK, GarageNetwork.BODYWORK, GarageNetwork.DEALER)
                : truck ? List.of(GarageNetwork.TRUCK)
                : List.of(GarageNetwork.DEALER, GarageNetwork.INDEPENDENT, GarageNetwork.INDEPENDENT, GarageNetwork.FAST_FIT);
        Map<GarageNetwork, List<GarageInfo>> regional = byRegion.getOrDefault(region, byRegion.get(Region.PAC));
        GarageNetwork wanted = networks.get(random.nextInt(networks.size()));
        List<GarageInfo> candidates = regional.getOrDefault(wanted, List.of());
        if (candidates.isEmpty()) {
            candidates = regional.values().stream().flatMap(List::stream).toList();
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private List<QuoteLineInput> lines(Scenario scenario, TariffClass tariffClass, GarageInfo garage) {
        List<QuoteLineInput> lines = new ArrayList<>();
        BigDecimal work = BigDecimal.ZERO;
        for (LineSpec spec : scenario.lines()) {
            QuoteLineInput line = switch (spec.kind()) {
                case PART -> partLine(spec, tariffClass, garage.behaviour());
                case LABOUR -> labourLine(spec, tariffClass, garage);
                case SUPPLIES -> null;
            };
            if (line != null) {
                work = work.add(line.quantity().multiply(line.unitPrice()));
            }
            lines.add(line);
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) == null) {
                double share = switch (garage.behaviour()) {
                    case FAIR -> between(0.03, 0.055);
                    case LOOSE -> between(0.05, 0.085);
                    case HEAVY -> between(0.09, 0.16);
                };
                BigDecimal amount = decimal(Math.max(8, work.doubleValue() * share), 2);
                lines.set(i, new QuoteLineInput(LineKind.SUPPLIES, null, scenario.lines().get(i).label(), BigDecimal.ONE,
                        amount));
            }
        }
        if (garage.behaviour() == Behaviour.HEAVY && random.nextDouble() < 0.3) {
            QuoteLineInput copied = lines.stream().filter(l -> l.kind() == LineKind.LABOUR).findFirst().orElse(lines.getFirst());
            lines.add(copied);
        }
        if (random.nextDouble() < 0.05) {
            LineSpec unknown = pick(QuoteDemoCatalog.UNKNOWN_LINES);
            BigDecimal price = unknown.kind() == LineKind.LABOUR ? garage.garage().getHourlyRate()
                    : decimal(between(40, 180), 0).setScale(2);
            lines.add(new QuoteLineInput(unknown.kind(), unknown.code(), unknown.label(),
                    BigDecimal.valueOf(unknown.quantity()), price));
        }
        return lines;
    }

    private QuoteLineInput partLine(LineSpec spec, TariffClass tariffClass, Behaviour behaviour) {
        double quantity = spec.code().equals("HUILE-5W30") && tariffClass == TariffClass.TRUCK ? 28 : spec.quantity();
        BigDecimal reference = tariff.part(spec.code(), tariffClass).map(Tariff.PartPrice::price)
                .orElse(BigDecimal.valueOf(50));
        double factor = switch (behaviour) {
            case FAIR -> random.nextDouble() < 0.85 ? between(0.94, 1.05) : between(1.05, 1.16);
            case LOOSE -> random.nextDouble() < 0.5 ? between(0.98, 1.08) : between(1.08, 1.24);
            case HEAVY -> random.nextDouble() < 0.25 ? between(1.0, 1.1) : between(1.15, 1.48);
        };
        return new QuoteLineInput(LineKind.PART, spec.code(), spec.label(), BigDecimal.valueOf(quantity),
                decimal(reference.doubleValue() * factor, 2));
    }

    private QuoteLineInput labourLine(LineSpec spec, TariffClass tariffClass, GarageInfo garage) {
        BigDecimal standard = tariff.operation(spec.code(), tariffClass).map(Tariff.StandardTime::hours)
                .orElse(BigDecimal.valueOf(spec.quantity()));
        double factor = switch (garage.behaviour()) {
            case FAIR -> random.nextDouble() < 0.9 ? 1.0 : between(1.0, 1.2);
            case LOOSE -> random.nextDouble() < 0.6 ? 1.0 : between(1.1, 1.35);
            case HEAVY -> random.nextDouble() < 0.3 ? 1.0 : between(1.2, 1.7);
        };
        BigDecimal hours = decimal(standard.doubleValue() * factor, 1).max(new BigDecimal("0.1"));
        return new QuoteLineInput(LineKind.LABOUR, spec.code(), spec.label(), hours, garage.garage().getHourlyRate());
    }

    private String unknownRegistration() {
        String letters = "ABCDEFGHJKLMNPQRSTVWXYZ";
        return "%c%c-%03d-%c%c".formatted(letters.charAt(random.nextInt(letters.length())),
                letters.charAt(random.nextInt(letters.length())), 100 + random.nextInt(900),
                letters.charAt(random.nextInt(letters.length())), letters.charAt(random.nextInt(letters.length())));
    }

    // ---------------------------------------------------------------- helpers

    /** A submission instant in the last {@code days} days, during working hours. */
    private Instant slot(int days) {
        LocalDate day = LocalDate.ofInstant(now, zone).minusDays(random.nextInt(days + 1));
        Instant at = day.atTime(7 + random.nextInt(11), random.nextInt(60)).atZone(zone).toInstant();
        Instant latest = now.minus(Duration.ofHours(2));
        return at.isAfter(latest) ? latest.minus(hours(0, 4)) : at;
    }

    private Duration hours(int min, int max) {
        return Duration.ofMinutes(min * 60L + random.nextInt((max - min) * 60 + 1));
    }

    private double between(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
