package com.fleetpilot.telemetry.infrastructure.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.fleetpilot.common.telemetry.ConnectedFleet;
import com.fleetpilot.telemetry.infrastructure.reference.FleetModule.Vehicle;

/**
 * Ninety days of usage for every vehicle in service, as the boxes would have reported it: the history the energy
 * transition study needs. Deterministic: the same fleet and the same day always give the same history.
 *
 * <ul>
 *   <li>cars: about 55 km a working day, a long trip (250 to 450 km) one day in twenty, little use at weekends;</li>
 *   <li>vans: about 90 km a working day, delivery rounds of many short trips;</li>
 *   <li>trucks: about 280 km a working day, on few long trips;</li>
 *   <li>site machinery: short distances.</li>
 * </ul>
 * Each vehicle has its own intensity, so that some cars barely move and others are always on the road.
 */
final class UsageHistoryGenerator {

    static final int DAYS = 90;

    /** One day of usage of one vehicle. */
    record Day(UUID vehicleId, UUID companyId, LocalDate day, BigDecimal distanceKm, int trips, BigDecimal longestTripKm,
            int drivingMinutes, int maxSpeed) {
    }

    private final long seed;
    private final LocalDate today;

    UsageHistoryGenerator(long seed, LocalDate today) {
        this.seed = seed;
        this.today = today;
    }

    List<Day> generate(List<Vehicle> fleet) {
        List<Day> days = new ArrayList<>();
        fleet.stream().filter(v -> !"DISPOSED".equals(v.status()) && !"AWAITING_DELIVERY".equals(v.status()))
                // only the vehicles fitted with a box have a usage history
                .filter(v -> ConnectedFleet.isEquipped(v.id()))
                .sorted(Comparator.comparing(Vehicle::id))
                .forEach(v -> days.addAll(vehicle(v)));
        return days;
    }

    private List<Day> vehicle(Vehicle v) {
        Random random = new Random(seed ^ v.id().getMostSignificantBits());
        double intensity = 0.45 + random.nextDouble() * 1.1;
        double baseKm = switch (v.category()) {
            case "VAN" -> 90;
            case "TRUCK" -> 280;
            case "MACHINE" -> 18;
            default -> 55;
        } * intensity;
        boolean electric = "ELECTRIC".equals(v.energy());
        List<Day> days = new ArrayList<>();
        for (int d = DAYS; d >= 1; d--) {
            LocalDate day = today.minusDays(d);
            boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            double useChance = weekend ? 0.12 : 0.88;
            if (random.nextDouble() > useChance) {
                continue;
            }
            double km = Math.max(3, baseKm * (0.55 + random.nextGaussian() * 0.25 + 0.45));
            boolean longTrip = "CAR".equals(v.category()) && random.nextDouble() < (electric ? 0.02 : 0.05);
            if (longTrip) {
                km = 250 + random.nextDouble() * 200;
            }
            int trips = switch (v.category()) {
                case "VAN" -> 5 + random.nextInt(8);
                case "TRUCK" -> 1 + random.nextInt(3);
                default -> longTrip ? 2 : 2 + random.nextInt(4);
            };
            double longest = longTrip ? km * (0.45 + random.nextDouble() * 0.1)
                    : km / trips * (1.2 + random.nextDouble() * 1.3);
            double averageSpeed = switch (v.category()) {
                case "TRUCK" -> 62;
                case "MACHINE" -> 25;
                default -> longTrip ? 88 : 38 + random.nextDouble() * 14;
            };
            int maxSpeed = (int) Math.min(topSpeed(v.category(), random), averageSpeed * (1.6 + random.nextDouble() * 0.6));
            days.add(new Day(v.id(), v.companyId(), day, decimal(km), trips, decimal(Math.min(longest, km)),
                    (int) Math.round(km / averageSpeed * 60), maxSpeed));
        }
        return days;
    }

    /**
     * The top speed a vehicle of the category may reach in a day: trucks are limited to 90 km/h by their speed
     * limiter, site machinery rarely leaves town, and a few cars and vans go over the motorway limit.
     */
    static int topSpeed(String category, Random random) {
        return switch (category) {
            case "TRUCK" -> 88 + random.nextInt(4);
            case "MACHINE" -> 45 + random.nextInt(25);
            case "VAN" -> 118 + random.nextInt(22);
            default -> 125 + random.nextInt(32);
        };
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
