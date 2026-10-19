package com.fleetpilot.energy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fleetpilot.energy.domain.Transition.Assessment;
import com.fleetpilot.energy.domain.Transition.Eligibility;
import com.fleetpilot.energy.domain.Transition.EvModel;
import com.fleetpilot.energy.domain.Transition.Prices;
import com.fleetpilot.energy.domain.Transition.Usage;
import com.fleetpilot.energy.domain.Transition.VehicleFacts;
import com.fleetpilot.energy.domain.Transition.ZfeRule;

/**
 * Can this vehicle go electric, with which model, at what cost and for how much CO2? Pure business rules, from the
 * real usage measured by the connected box when there is one.
 *
 * <ol>
 *   <li>range needed: the 95th percentile of the daily distance, plus 15 % of reserve;</li>
 *   <li>model: the cheapest electric model of the vehicle's segment (or a larger one) whose realistic range (70 % of
 *       WLTP) covers it;</li>
 *   <li>eligibility: no day beyond the range, a few days (8 % of the days driven) needing a fast charge, or not yet;</li>
 *   <li>annual cost: rent, energy, maintenance and the annual taxes of company cars (CO2 and pollutants, barème 2024),
 *       today and in electric; CO2 from the WLTP emissions and the French grid mix;</li>
 *   <li>renewal: the end of the lease, or the holding period of an owned vehicle (5 years for a car, 6 for a van, 8
 *       for a truck); low emission zone of the site.</li>
 * </ol>
 */
public final class TransitionAnalysis {

    /** Share of the WLTP range a fleet can count on all year. */
    public static final double REALISTIC_SHARE = 0.70;
    static final double RESERVE = 1.15;
    static final double CHARGING_DAYS_SHARE = 0.08;
    static final int MIN_ACTIVE_DAYS = 15;
    static final double EV_MAINTENANCE_SHARE = 0.70;
    static final BigDecimal MONTHS = BigDecimal.valueOf(12);

    private static final Set<String> ZERO_EMISSION = Set.of("ELECTRIC", "HYDROGEN");
    private static final List<String> CITY_CARS = List.of("clio", "208", "yaris", "polo", "c3", "twingo", "corsa",
            "sandero", "i20", "fiesta", "ibiza", "fabia", "micra", "500");
    private static final List<String> LARGE_CARS = List.of("508", "5008", "superb", "passat", "a6", "x3", "kodiaq",
            "tiguan", "espace", "talisman", "model", "c5", "octavia combi", "série 3", "série 5", "classe c",
            "classe e", "a4");
    /** Road tractors (40 to 44 t): no electric equivalent in the catalogue, which offers 16 t distribution trucks. */
    private static final List<String> ROAD_TRACTORS = List.of("s-way", "actros", "fh", "tgx", "xf", "t high",
            "t-high", "stralis", "r 450", "r450");
    private static final List<String> SMALL_VANS = List.of("kangoo", "berlingo", "partner", "caddy", "doblo", "citan",
            "combo", "townstar");
    private static final List<String> LARGE_VANS = List.of("master", "sprinter", "crafter", "ducato", "boxer",
            "jumper", "daily", "movano", "transit 350", "e-transit");
    /** Segments from the smallest to the largest, per category: a vehicle is replaced by its size or a larger one. */
    private static final Map<String, List<String>> SEGMENT_ORDER = Map.of(
            "CAR", List.of("CITY", "COMPACT", "LARGE"),
            "VAN", List.of("SMALL", "MEDIUM", "LARGE"),
            "TRUCK", List.of("TRUCK"));

    /** Litres (or kg of gas) per 100 km, by category and energy. */
    private static final Map<String, Double> CONSUMPTION = Map.ofEntries(
            Map.entry("CAR/DIESEL", 5.8), Map.entry("CAR/PETROL", 7.0), Map.entry("CAR/HYBRID", 5.0),
            Map.entry("CAR/PLUG_IN_HYBRID", 4.2), Map.entry("CAR/LPG", 8.6), Map.entry("CAR/CNG", 4.6),
            Map.entry("VAN/DIESEL", 8.2), Map.entry("VAN/PETROL", 9.6), Map.entry("VAN/HYBRID", 7.2),
            Map.entry("VAN/CNG", 6.4), Map.entry("TRUCK/DIESEL", 26.0), Map.entry("TRUCK/CNG", 27.0));
    private static final Map<String, Double> MAINTENANCE_PER_KM = Map.of("CAR", 0.040, "VAN", 0.055, "TRUCK", 0.120);
    /** Monthly cost of an owned vehicle (depreciation and financing), to compare with a lease. */
    private static final Map<String, Integer> OWNED_MONTHLY = Map.of("CAR", 420, "VAN", 480, "TRUCK", 1_900);
    private static final Map<String, Integer> HOLDING_YEARS = Map.of("CAR", 5, "VAN", 6, "TRUCK", 8, "MACHINE", 10);

    private final List<EvModel> catalog;
    private final List<ZfeRule> zones;
    private final Prices prices;
    private final LocalDate today;

    public TransitionAnalysis(List<EvModel> catalog, List<ZfeRule> zones, Prices prices, LocalDate today) {
        this.catalog = List.copyOf(catalog);
        this.zones = List.copyOf(zones);
        this.prices = prices;
        this.today = today;
    }

    public Assessment assess(VehicleFacts v, Usage usage) {
        LocalDate renewal = renewal(v);
        Optional<ZfeRule> zone = zone(v);
        String zfeZone = zone.map(ZfeRule::zone).orElse(null);
        LocalDate bannedFrom = zone.map(ZfeRule::effectiveFrom).orElse(null);

        boolean measured = usage != null && usage.dailyKm().size() >= MIN_ACTIVE_DAYS;
        int annualKm = measured ? annualKm(usage) : estimatedAnnualKm(v);
        String source = measured ? "TELEMETRY" : "REGISTER";
        Integer activeDays = measured ? usage.dailyKm().size() : null;
        BigDecimal p95 = measured ? percentile95(usage.dailyKm()) : scale(annualKm / 220.0 * 1.6);
        BigDecimal longest = measured ? usage.dailyKm().stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO)
                : null;

        if (ZERO_EMISSION.contains(v.energy())) {
            return new Assessment(v, Eligibility.ZERO_EMISSION, source, "Véhicule déjà à zéro émission", annualKm,
                    activeDays, p95, longest, null, null, null, null, null, null, 0, null, renewal, null, null);
        }
        String outOfCatalogue = "MACHINE".equals(v.category())
                ? "Pas d'équivalent électrique au catalogue pour les engins de chantier"
                : isRoadTractor(v) ? "Tracteur routier : le catalogue ne propose que des porteurs électriques de 16 t"
                : null;
        if (outOfCatalogue != null) {
            return new Assessment(v, Eligibility.NOT_APPLICABLE, source, outOfCatalogue, annualKm, activeDays, p95,
                    longest, null, null, null, null, null, null, co2Kg(v, annualKm), null, renewal, zfeZone, bannedFrom);
        }

        int required = (int) Math.ceil(p95.doubleValue() * RESERVE);
        EvModel model = model(v, required);
        int range = model.realisticRangeKm();
        Integer beyond = measured ? (int) usage.dailyKm().stream().filter(d -> d.intValue() > range).count() : null;
        Eligibility status;
        String reason;
        if (!measured && required > range) {
            status = Eligibility.NOT_YET;
            reason = ("Estimation d'après le compteur : environ %s km par an, soit des journées d'environ %d km, au-delà "
                    + "des %d km d'autonomie réelle du modèle le plus endurant").formatted(thousands(annualKm),
                    p95.intValue(), range);
        } else if (!measured) {
            status = Eligibility.TO_CONFIRM;
            reason = "Estimation d'après le compteur : environ %s km par an. Un boîtier connecté confirmerait l'usage réel"
                    .formatted(thousands(annualKm));
        } else if (beyond == 0) {
            status = Eligibility.ELIGIBLE;
            reason = "Tous les jours roulés tiennent dans l'autonomie réelle de %d km (plus long jour : %d km), recharge au dépôt"
                    .formatted(range, longest.intValue());
        } else if (beyond <= Math.max(1, Math.round(activeDays * CHARGING_DAYS_SHARE))) {
            status = Eligibility.ELIGIBLE_WITH_CHARGING;
            reason = "%d jour%s sur %d dépasse%s l'autonomie réelle de %d km : une recharge rapide en route ces jours-là"
                    .formatted(beyond, beyond > 1 ? "s" : "", activeDays, beyond > 1 ? "nt" : "", range);
        } else {
            status = Eligibility.NOT_YET;
            reason = "%d jours sur %d dépassent l'autonomie réelle de %d km : trop fréquent pour l'électrique aujourd'hui"
                    .formatted(beyond, activeDays, range);
        }

        BigDecimal current = currentCost(v, annualKm);
        double fastShare = measured ? 0.10 + (double) beyond / activeDays : 0.15;
        BigDecimal ev = evCost(v, model, annualKm, fastShare);
        int co2Ev = (int) Math.round(annualKm / 100.0 * model.consumptionKwh100km().doubleValue()
                * prices.gridKgCo2PerKwh().doubleValue());
        return new Assessment(v, status, source, reason, annualKm, activeDays, p95, longest, beyond, required, model,
                current, ev, current.subtract(ev), co2Kg(v, annualKm), co2Ev, renewal, zfeZone, bannedFrom);
    }

    // ------------------------------------------------------------------------------------------------------ usage

    static int annualKm(Usage usage) {
        double total = usage.dailyKm().stream().mapToDouble(BigDecimal::doubleValue).sum();
        return (int) Math.round(total * 365 / Math.max(1, usage.observedDays()));
    }

    int estimatedAnnualKm(VehicleFacts v) {
        double years = Math.max(0.5, ChronoUnit.DAYS.between(v.firstRegistration(), today) / 365.0);
        return (int) Math.min(80_000, Math.round(v.mileage() / years));
    }

    static BigDecimal percentile95(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index)).setScale(1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------------------------------------------ model

    /** The cheapest model of the segment or a larger one covering the range, else the one going furthest. */
    EvModel model(VehicleFacts v, int requiredKm) {
        List<String> order = SEGMENT_ORDER.getOrDefault(v.category(), List.of());
        int size = order.indexOf(segment(v));
        List<EvModel> candidates = catalog.stream().filter(m -> m.category().equals(v.category()))
                .filter(m -> order.indexOf(m.segment()) >= size)
                .toList();
        return candidates.stream().filter(m -> m.realisticRangeKm() >= requiredKm)
                .min(Comparator.comparing(EvModel::monthlyRent))
                .or(() -> candidates.stream().max(Comparator.comparingInt(EvModel::wltpRangeKm)))
                .orElseThrow(() -> new IllegalStateException("No electric model for " + v.category()));
    }

    static boolean isRoadTractor(VehicleFacts v) {
        String model = (v.model() == null ? "" : v.model()).toLowerCase(Locale.ROOT);
        return "TRUCK".equals(v.category()) && ROAD_TRACTORS.stream().anyMatch(model::contains);
    }

    static String segment(VehicleFacts v) {
        String model = (v.model() == null ? "" : v.model()).toLowerCase(Locale.ROOT);
        return switch (v.category()) {
            case "CAR" -> LARGE_CARS.stream().anyMatch(model::contains) ? "LARGE"
                    : CITY_CARS.stream().anyMatch(model::contains) ? "CITY" : "COMPACT";
            case "VAN" -> SMALL_VANS.stream().anyMatch(model::contains) ? "SMALL"
                    : LARGE_VANS.stream().anyMatch(model::contains) ? "LARGE" : "MEDIUM";
            default -> "TRUCK";
        };
    }

    // ------------------------------------------------------------------------------------------------------ costs

    BigDecimal currentCost(VehicleFacts v, int annualKm) {
        double consumption = CONSUMPTION.getOrDefault(v.category() + "/" + v.energy(),
                CONSUMPTION.getOrDefault(v.category() + "/DIESEL", 6.0));
        BigDecimal fuel = BigDecimal.valueOf(annualKm / 100.0 * consumption).multiply(fuelPrice(v.energy()));
        BigDecimal maintenance = BigDecimal.valueOf(annualKm * MAINTENANCE_PER_KM.getOrDefault(v.category(), 0.05));
        return scale(fuel.add(maintenance).add(rent(v).multiply(MONTHS)).add(companyCarTax(v)).doubleValue());
    }

    BigDecimal evCost(VehicleFacts v, EvModel model, int annualKm, double fastShare) {
        double share = Math.min(1, fastShare);
        double kwhPrice = prices.depotKwh().doubleValue() * (1 - share) + prices.publicKwh().doubleValue() * share;
        double energy = annualKm / 100.0 * model.consumptionKwh100km().doubleValue() * kwhPrice;
        double maintenance = annualKm * MAINTENANCE_PER_KM.getOrDefault(v.category(), 0.05) * EV_MAINTENANCE_SHARE;
        return scale(energy + maintenance + model.monthlyRent().doubleValue() * 12);
    }

    private BigDecimal rent(VehicleFacts v) {
        return v.monthlyRent() != null ? v.monthlyRent()
                : BigDecimal.valueOf(OWNED_MONTHLY.getOrDefault(v.category(), 450));
    }

    private BigDecimal fuelPrice(String energy) {
        return switch (energy) {
            case "PETROL", "HYBRID", "PLUG_IN_HYBRID" -> prices.petrol();
            case "LPG" -> prices.lpg();
            case "CNG" -> prices.cng();
            default -> prices.diesel();
        };
    }

    /**
     * Annual taxes of a company car (taxe annuelle sur les émissions de CO2, barème WLTP 2024, plus taxe sur les
     * polluants): vans and trucks are not concerned, electric cars are exempt.
     */
    static BigDecimal companyCarTax(VehicleFacts v) {
        if (!"CAR".equals(v.category())) {
            return BigDecimal.ZERO;
        }
        int[][] brackets = {{14, 0}, {55, 1}, {63, 2}, {95, 3}, {145, 4}, {170, 10}, {190, 50}, {Integer.MAX_VALUE, 60}};
        int grams = v.co2GramsPerKm();
        int tax = 0;
        int previous = 0;
        for (int[] bracket : brackets) {
            if (grams <= previous) {
                break;
            }
            tax += (Math.min(grams, bracket[0]) - previous) * bracket[1];
            previous = bracket[0];
        }
        int pollutants = "DIESEL".equals(v.energy()) ? 40 : 20;
        return BigDecimal.valueOf(tax + pollutants);
    }

    private static int co2Kg(VehicleFacts v, int annualKm) {
        return (int) Math.round(annualKm * (double) v.co2GramsPerKm() / 1000);
    }

    // ------------------------------------------------------------------------------------------------- renewal, ZFE

    LocalDate renewal(VehicleFacts v) {
        LocalDate date = v.contractEnd() != null ? v.contractEnd()
                : v.firstRegistration().plusYears(HOLDING_YEARS.getOrDefault(v.category(), 6));
        // a vehicle kept beyond its term is renewed in the coming quarter
        return date.isBefore(today) ? today.plusMonths(3) : date;
    }

    Optional<ZfeRule> zone(VehicleFacts v) {
        return zones.stream().filter(z -> z.city().equalsIgnoreCase(v.siteCity() == null ? "" : v.siteCity()))
                .filter(z -> v.critAir() > z.maxCritAir()).findFirst();
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String thousands(int value) {
        return String.format(Locale.FRANCE, "%,d", value);
    }
}
