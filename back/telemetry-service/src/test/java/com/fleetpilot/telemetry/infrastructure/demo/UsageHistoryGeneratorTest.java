package com.fleetpilot.telemetry.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.telemetry.ConnectedFleet;
import com.fleetpilot.telemetry.infrastructure.reference.FleetModule.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ninety days of demo usage")
class UsageHistoryGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final UUID COMPANY = UUID.randomUUID();

    private static List<Vehicle> fleet() {
        List<Vehicle> fleet = new ArrayList<>();
        String[] categories = {"CAR", "CAR", "VAN", "TRUCK"};
        for (int i = 0; i < 80; i++) {
            fleet.add(new Vehicle(UUID.nameUUIDFromBytes(("v" + i).getBytes()), COMPANY, "AB-%03d-CD".formatted(i),
                    categories[i % 4], i % 5 == 0 ? "ELECTRIC" : "DIESEL", "IN_SERVICE", 40_000));
        }
        fleet.add(new Vehicle(UUID.randomUUID(), COMPANY, "ZZ-001-ZZ", "CAR", "DIESEL", "DISPOSED", 120_000));
        fleet.add(new Vehicle(UUID.randomUUID(), COMPANY, "ZZ-002-ZZ", "CAR", "DIESEL", "AWAITING_DELIVERY", 0));
        return fleet;
    }

    private final List<UsageHistoryGenerator.Day> days = new UsageHistoryGenerator(1L, TODAY).generate(fleet());

    @Test
    void the_same_fleet_always_gives_the_same_history() {
        assertThat(new UsageHistoryGenerator(1L, TODAY).generate(fleet())).isEqualTo(days);
    }

    @Test
    void only_vehicles_in_service_have_a_history_over_the_ninety_days_before_today() {
        // only the vehicles fitted with a box (about four in five) have a history
        List<UUID> equipped = fleet().stream().filter(v -> "IN_SERVICE".equals(v.status()))
                .map(Vehicle::id).filter(ConnectedFleet::isEquipped).toList();
        assertThat(days.stream().map(UsageHistoryGenerator.Day::vehicleId).distinct())
                .containsExactlyInAnyOrderElementsOf(equipped);
        assertThat(equipped.size()).isBetween(56, 72);
        assertThat(days).allSatisfy(d -> assertThat(d.day()).isBetween(TODAY.minusDays(90), TODAY.minusDays(1)));
    }

    @Test
    void vehicles_are_used_on_working_days_much_more_than_at_weekends() {
        long weekend = days.stream().filter(d -> d.day().getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue())
                .count();
        assertThat((double) weekend / days.size()).isLessThan(0.1);
    }

    @Test
    void trucks_drive_further_than_cars_and_a_trip_is_never_longer_than_its_day() {
        assertThat(averageKm("TRUCK")).isGreaterThan(averageKm("CAR") * 3);
        assertThat(days).allSatisfy(d -> {
            assertThat(d.longestTripKm()).isLessThanOrEqualTo(d.distanceKm());
            assertThat(d.trips()).isPositive();
            assertThat(d.maxSpeed()).isBetween(20, 156);
        });
    }

    @Test
    void trucks_stay_under_their_speed_limiter() {
        List<UUID> trucks = fleet().stream().filter(v -> v.category().equals("TRUCK")).map(Vehicle::id).toList();
        assertThat(days).filteredOn(d -> trucks.contains(d.vehicleId()))
                .allSatisfy(d -> assertThat(d.maxSpeed()).isLessThanOrEqualTo(91));
    }

    @Test
    void some_cars_make_long_trips_that_an_electric_car_could_not_do_without_charging() {
        assertThat(days).anySatisfy(d -> assertThat(d.distanceKm()).isGreaterThan(BigDecimal.valueOf(250)));
    }

    private double averageKm(String category) {
        List<UUID> ids = fleet().stream().filter(v -> v.category().equals(category)).map(Vehicle::id).toList();
        return days.stream().filter(d -> ids.contains(d.vehicleId()))
                .mapToDouble(d -> d.distanceKm().doubleValue()).average().orElseThrow();
    }
}
