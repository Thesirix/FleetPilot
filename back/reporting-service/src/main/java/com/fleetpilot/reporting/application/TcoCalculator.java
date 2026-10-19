package com.fleetpilot.reporting.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.reporting.application.Sources.Contract;
import com.fleetpilot.reporting.application.Sources.Incident;
import com.fleetpilot.reporting.application.Sources.Quote;
import com.fleetpilot.reporting.application.Sources.Vehicle;

/**
 * Total cost of ownership over the last twelve months, per vehicle and for the fleet.
 *
 * <ul>
 *   <li>rent: monthly rent of the active lease contract x 12 (a vehicle bought outright has no rent here);</li>
 *   <li>maintenance: accepted amounts of the garage quotes of the twelve months;</li>
 *   <li>claims: estimated cost of the claims of the twelve months;</li>
 *   <li>kilometres: the daily average of the contract projection x 365, when known.</li>
 * </ul>
 */
public final class TcoCalculator {

    private static final Set<String> DECIDED = Set.of("ACCEPTED", "COMPLETED", "INVOICED");
    private static final Set<String> IMMOBILIZED = Set.of("IN_REPAIR", "IMMOBILIZED");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    /** Cost of one vehicle over twelve months. Kilometres and cost per kilometre may be null. */
    public record VehicleCost(Vehicle vehicle, String lessor, BigDecimal monthlyRent, BigDecimal rent,
            BigDecimal maintenance, BigDecimal claims, BigDecimal total, Integer kilometres, BigDecimal costPerKm) {
    }

    /**
     * Indicators of the fleet.
     *
     * @param monthlyCostPerVehicle total cost of the twelve months / 12 / vehicles
     * @param claimsPer100Vehicles  claims of the twelve months for 100 vehicles
     * @param immobilisationRate    share of the vehicles at the garage or immobilized, in percent
     */
    public record Indicators(int vehicles, BigDecimal totalCost, BigDecimal monthlyCostPerVehicle,
            BigDecimal costPerKm, BigDecimal rent, BigDecimal maintenance, BigDecimal claims,
            BigDecimal claimsPer100Vehicles, BigDecimal immobilisationRate, BigDecimal leasedShare, LocalDate asOf) {
    }

    private final Instant since;
    private final LocalDate today;

    public TcoCalculator(Instant now, ZoneId zone) {
        this.today = LocalDate.ofInstant(now, zone);
        this.since = today.minusYears(1).atStartOfDay(zone).toInstant();
    }

    public List<VehicleCost> costs(List<Vehicle> vehicles, List<Contract> contracts, List<Quote> quotes,
            List<Incident> incidents) {
        Map<UUID, Contract> contractByVehicle = contracts.stream().filter(c -> c.vehicle() != null)
                .collect(Collectors.toMap(c -> c.vehicle().id(), Function.identity(), (a, b) -> a));
        Map<UUID, BigDecimal> maintenance = quotes.stream()
                .filter(q -> q.vehicle() != null && DECIDED.contains(q.status()) && q.acceptedAmount() != null
                        && !q.submittedAt().isBefore(since))
                .collect(Collectors.groupingBy(q -> q.vehicle().id(),
                        Collectors.reducing(BigDecimal.ZERO, Quote::acceptedAmount, BigDecimal::add)));
        Map<UUID, BigDecimal> claims = incidents.stream()
                .filter(i -> i.vehicle() != null && i.estimatedCost() != null && !i.occurredAt().isBefore(since))
                .collect(Collectors.groupingBy(i -> i.vehicle().id(),
                        Collectors.reducing(BigDecimal.ZERO, Incident::estimatedCost, BigDecimal::add)));
        return vehicles.stream()
                .filter(v -> !"DISPOSED".equals(v.status()))
                .map(v -> cost(v, contractByVehicle.get(v.id()), maintenance.getOrDefault(v.id(), BigDecimal.ZERO),
                        claims.getOrDefault(v.id(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(VehicleCost::total).reversed())
                .toList();
    }

    private static VehicleCost cost(Vehicle vehicle, Contract contract, BigDecimal maintenance, BigDecimal claims) {
        BigDecimal monthlyRent = contract == null || contract.monthlyRent() == null ? null : contract.monthlyRent();
        BigDecimal rent = monthlyRent == null ? BigDecimal.ZERO : monthlyRent.multiply(TWELVE);
        BigDecimal total = money(rent.add(maintenance).add(claims));
        Integer kilometres = contract == null || contract.projection() == null
                || contract.projection().dailyAverageKm() == null ? null
                : (int) Math.round(contract.projection().dailyAverageKm() * 365);
        BigDecimal costPerKm = kilometres == null || kilometres == 0 ? null
                : total.divide(BigDecimal.valueOf(kilometres), 3, RoundingMode.HALF_UP);
        return new VehicleCost(vehicle, contract == null ? null : contract.lessor(), monthlyRent, money(rent),
                money(maintenance), money(claims), total, kilometres, costPerKm);
    }

    public Indicators indicators(List<Vehicle> vehicles, List<VehicleCost> costs, List<Incident> incidents) {
        List<Vehicle> inFleet = vehicles.stream().filter(v -> !"DISPOSED".equals(v.status())).toList();
        int count = inFleet.size();
        BigDecimal total = sum(costs, VehicleCost::total);
        BigDecimal kmCost = costs.stream().filter(c -> c.kilometres() != null).map(VehicleCost::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long kilometres = costs.stream().filter(c -> c.kilometres() != null).mapToLong(VehicleCost::kilometres).sum();
        long claimCount = incidents.stream().filter(i -> !i.occurredAt().isBefore(since)).count();
        long immobilized = inFleet.stream().filter(v -> IMMOBILIZED.contains(v.status())).count();
        long leased = costs.stream().filter(c -> c.monthlyRent() != null).count();
        return new Indicators(count, total,
                count == 0 ? BigDecimal.ZERO : total.divide(TWELVE.multiply(BigDecimal.valueOf(count)), 2, RoundingMode.HALF_UP),
                kilometres == 0 ? null : kmCost.divide(BigDecimal.valueOf(kilometres), 3, RoundingMode.HALF_UP),
                sum(costs, VehicleCost::rent), sum(costs, VehicleCost::maintenance), sum(costs, VehicleCost::claims),
                ratio(claimCount * 100, count, 1), ratio(immobilized * 100, count, 1), ratio(leased * 100, count, 1),
                today);
    }

    private static BigDecimal sum(List<VehicleCost> costs, Function<VehicleCost, BigDecimal> field) {
        return money(costs.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal ratio(long numerator, int denominator, int scale) {
        return denominator == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), scale, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
