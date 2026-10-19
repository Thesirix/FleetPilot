package com.fleetpilot.vehicle.domain.model;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * French rules and manufacturer intervals used to schedule the deadlines of a vehicle.
 *
 * <ul>
 *   <li>Technical inspection (contrôle technique): cars and vans 4 years after first registration, then every
 *       2 years; trucks 1 year after first registration, then every year. Machinery is not concerned.</li>
 *   <li>Service: every 12 months or a mileage interval depending on the category (24 months and 30,000 km for
 *       electric cars, which need less maintenance).</li>
 *   <li>Tyres: by mileage only.</li>
 *   <li>Tachograph calibration: trucks, every 2 years.</li>
 * </ul>
 *
 * The policy is a pure function of its inputs, so schedules are reproducible and easy to test.
 */
public final class DeadlinePolicy {

    /** What the next occurrence of a deadline should be. */
    public record Due(DeadlineType type, LocalDate dueDate, Integer dueMileage) {
    }

    private record ServiceInterval(Period period, Integer kilometres) {
    }

    private DeadlinePolicy() {
    }

    /** Deadlines of a vehicle entering the fleet, assuming past obligations were met on schedule. */
    public static List<Due> initialSchedule(VehicleCategory category, Energy energy, LocalDate firstRegistration,
            int mileage, LocalDate today) {
        List<Due> schedule = new ArrayList<>();
        inspectionPeriod(category).ifPresent(period -> schedule.add(new Due(DeadlineType.TECHNICAL_INSPECTION,
                nextOccurrence(firstRegistration.plus(firstInspectionDelay(category)), period, today), null)));

        ServiceInterval service = serviceInterval(category, energy);
        schedule.add(new Due(DeadlineType.SERVICE, nextOccurrence(firstRegistration.plus(service.period()),
                service.period(), today), nextMultiple(mileage, service.kilometres())));

        tyreInterval(category).ifPresent(km -> schedule.add(new Due(DeadlineType.TYRES, null, nextMultiple(mileage, km))));

        if (category == VehicleCategory.TRUCK) {
            schedule.add(new Due(DeadlineType.TACHOGRAPH,
                    nextOccurrence(firstRegistration.plusYears(2), Period.ofYears(2), today), null));
        }
        return schedule;
    }

    /** The deadline that follows a completed one, or empty for one-off deadlines (end of contract). */
    public static Optional<Due> next(DeadlineType type, VehicleCategory category, Energy energy, LocalDate completedOn,
            int completedMileage) {
        return switch (type) {
            case TECHNICAL_INSPECTION -> inspectionPeriod(category)
                    .map(period -> new Due(type, completedOn.plus(period), null));
            case SERVICE -> {
                ServiceInterval interval = serviceInterval(category, energy);
                Integer dueMileage = interval.kilometres() == null ? null : completedMileage + interval.kilometres();
                yield Optional.of(new Due(type, completedOn.plus(interval.period()), dueMileage));
            }
            case TYRES -> tyreInterval(category).map(km -> new Due(type, null, completedMileage + km));
            case TACHOGRAPH -> Optional.of(new Due(type, completedOn.plusYears(2), null));
            case CONTRACT_END -> Optional.empty();
        };
    }

    private static Optional<Period> inspectionPeriod(VehicleCategory category) {
        return switch (category) {
            case CAR, VAN -> Optional.of(Period.ofYears(2));
            case TRUCK -> Optional.of(Period.ofYears(1));
            case MACHINE -> Optional.empty();
        };
    }

    private static Period firstInspectionDelay(VehicleCategory category) {
        return category == VehicleCategory.TRUCK ? Period.ofYears(1) : Period.ofYears(4);
    }

    private static ServiceInterval serviceInterval(VehicleCategory category, Energy energy) {
        return switch (category) {
            case CAR -> energy == Energy.ELECTRIC
                    ? new ServiceInterval(Period.ofYears(2), 30_000)
                    : new ServiceInterval(Period.ofYears(1), 20_000);
            case VAN -> new ServiceInterval(Period.ofYears(1), 30_000);
            case TRUCK -> new ServiceInterval(Period.ofYears(1), 60_000);
            case MACHINE -> new ServiceInterval(Period.ofYears(1), null);
        };
    }

    private static Optional<Integer> tyreInterval(VehicleCategory category) {
        return switch (category) {
            case CAR, VAN -> Optional.of(40_000);
            case TRUCK -> Optional.of(100_000);
            case MACHINE -> Optional.empty();
        };
    }

    /** First date of the series {@code first, first + period, ...} that is not before today. */
    static LocalDate nextOccurrence(LocalDate first, Period period, LocalDate today) {
        LocalDate date = first;
        while (date.isBefore(today)) {
            date = date.plus(period);
        }
        return date;
    }

    /** Next multiple of the interval strictly above the current mileage, or null when there is no interval. */
    static Integer nextMultiple(int mileage, Integer interval) {
        if (interval == null) {
            return null;
        }
        return (mileage / interval + 1) * interval;
    }
}
