package com.fleetpilot.telemetry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.telemetry.domain.LiveVehicle.AlertCandidate;
import com.fleetpilot.telemetry.domain.LiveVehicle.Observation;
import com.fleetpilot.telemetry.domain.LiveVehicle.VehicleInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rules applied to the positions of a connected vehicle")
class LiveVehicleTest {

    private static final UUID VEHICLE = UUID.randomUUID();
    private static final UUID COMPANY = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-10-14T07:00:00Z");
    private static final VehicleInfo CAR = new VehicleInfo("CAR", "DIESEL", null);
    private static final VehicleInfo TRUCK = new VehicleInfo("TRUCK", "DIESEL", null);
    private static final VehicleInfo EV = new VehicleInfo("CAR", "ELECTRIC", null);

    private final LiveVehicle vehicle = new LiveVehicle();
    private final List<AlertCandidate> alerts = new ArrayList<>();

    /** Position {@code second} seconds after the start, about 25 m further east per km/h-second driven. */
    private static PositionReport at(int second, double lon, int speed, boolean ignition, Integer battery) {
        return new PositionReport(VEHICLE, COMPANY, "AB-123-CD", START.plusSeconds(second), 43.3, lon, speed, 90,
                ignition, battery);
    }

    private Observation observe(PositionReport report, VehicleInfo info) {
        Observation observation = vehicle.observe(report, info);
        alerts.addAll(observation.alerts());
        return observation;
    }

    @Test
    void a_trip_runs_from_ignition_on_to_ignition_off_and_measures_distance_and_speeds() {
        observe(at(0, 5.370, 0, true, null), CAR);
        // 11 positions, 0.01 degree of longitude apart (about 0.81 km at 43.3 N)
        for (int i = 1; i <= 10; i++) {
            observe(at(i * 30, 5.370 + i * 0.01, 90 + i, true, null), CAR);
        }
        Observation end = observe(at(330, 5.470, 0, false, null), CAR);

        assertThat(end.finishedTrip()).isNotNull();
        assertThat(end.finishedTrip().distanceKm()).isBetween(8.0, 8.2);
        assertThat(end.finishedTrip().maxSpeed()).isEqualTo(100);
        assertThat(end.finishedTrip().startedAt()).isEqualTo(START);
        assertThat(end.finishedTrip().endedAt()).isEqualTo(START.plusSeconds(330));
    }

    @Test
    void a_trip_of_a_few_metres_is_not_kept() {
        observe(at(0, 5.370, 0, true, null), CAR);
        observe(at(60, 5.3701, 3, true, null), CAR);
        assertThat(observe(at(120, 5.3702, 0, false, null), CAR).finishedTrip()).isNull();
    }

    @Test
    void speeding_is_raised_once_after_three_positions_over_the_limit_plus_ten() {
        observe(at(0, 5.37, 140, true, null), CAR);
        observe(at(2, 5.38, 141, true, null), CAR);
        observe(at(4, 5.39, 145, true, null), CAR);
        assertThat(alerts).isEmpty();
        observe(at(6, 5.40, 152, true, null), CAR);
        observe(at(8, 5.41, 150, true, null), CAR);

        assertThat(alerts).singleElement().satisfies(alert -> {
            assertThat(alert.type()).isEqualTo(Alert.Type.SPEEDING);
            assertThat(alert.message()).isEqualTo("152 km/h pour une limite de 130 km/h");
        });
    }

    @Test
    void the_limit_depends_on_the_category_and_a_short_peak_is_tolerated() {
        observe(at(0, 5.37, 101, true, null), TRUCK);
        observe(at(2, 5.38, 102, true, null), TRUCK);
        observe(at(4, 5.39, 95, true, null), TRUCK);
        observe(at(6, 5.40, 103, true, null), TRUCK);
        assertThat(alerts).isEmpty();
        observe(at(8, 5.41, 104, true, null), TRUCK);
        observe(at(10, 5.42, 105, true, null), TRUCK);
        assertThat(alerts).extracting(AlertCandidate::type).containsExactly(Alert.Type.SPEEDING);
        assertThat(alerts.getFirst().message()).endsWith("limite de 90 km/h");
    }

    @Test
    void long_idle_is_raised_after_ten_minutes_engine_running_without_moving() {
        for (int minute = 0; minute <= 9; minute++) {
            observe(at(minute * 60, 5.37, 0, true, null), CAR);
        }
        assertThat(alerts).isEmpty();
        observe(at(600, 5.37, 0, true, null), CAR);
        observe(at(660, 5.37, 0, true, null), CAR);
        assertThat(alerts).extracting(AlertCandidate::type).containsExactly(Alert.Type.LONG_IDLE);
    }

    @Test
    void a_parked_vehicle_does_not_idle() {
        for (int minute = 0; minute <= 30; minute++) {
            observe(at(minute * 60, 5.37, 0, false, null), CAR);
        }
        assertThat(alerts).isEmpty();
    }

    @Test
    void low_battery_is_raised_once_while_driving_and_again_only_after_a_charge() {
        observe(at(0, 5.37, 50, true, 16), EV);
        observe(at(2, 5.38, 50, true, 14), EV);
        observe(at(4, 5.39, 50, true, 13), EV);
        assertThat(alerts).extracting(AlertCandidate::type).containsExactly(Alert.Type.LOW_BATTERY);

        observe(at(3600, 5.39, 0, false, 80), EV);
        observe(at(7200, 5.40, 60, true, 12), EV);
        assertThat(alerts).hasSize(2);
    }

    @Test
    void positions_are_sampled_every_thirty_seconds_and_at_each_ignition_change() {
        assertThat(observe(at(0, 5.37, 40, true, null), CAR).keepInHistory()).isTrue();
        assertThat(observe(at(10, 5.38, 40, true, null), CAR).keepInHistory()).isFalse();
        assertThat(observe(at(30, 5.39, 40, true, null), CAR).keepInHistory()).isTrue();
        assertThat(observe(at(32, 5.39, 0, false, null), CAR).keepInHistory()).isTrue();
    }

    @Test
    void a_late_position_does_not_change_the_trip() {
        observe(at(60, 5.37, 40, true, null), CAR);
        Observation late = observe(at(30, 5.30, 160, true, null), CAR);
        assertThat(late.keepInHistory()).isFalse();
        assertThat(late.alerts()).isEmpty();
    }

    @Test
    void the_service_is_announced_within_500_km_and_reported_late_once_passed() {
        VehicleInfo due = new VehicleInfo("CAR", "DIESEL", 60_000);
        assertThat(LiveVehicle.serviceDue(59_400, due)).isEmpty();
        assertThat(LiveVehicle.serviceDue(59_650, due)).hasValueSatisfying(alert -> {
            assertThat(alert.severity()).isEqualTo(Alert.Severity.INFO);
            assertThat(alert.message()).isEqualTo("Révision dans 350 km (à 60\u202F000 km)");
        });
        assertThat(LiveVehicle.serviceDue(60_120, due)).hasValueSatisfying(alert ->
                assertThat(alert.severity()).isEqualTo(Alert.Severity.WARNING));
        assertThat(LiveVehicle.serviceDue(60_120, CAR)).isEmpty();
    }

    @Test
    void the_distance_between_two_points_is_the_great_circle_distance() {
        // Marseille Vieux-Port to Aix-en-Provence centre: about 25 km as the crow flies
        assertThat(LiveVehicle.distanceKm(43.2951, 5.3740, 43.5263, 5.4454)).isBetween(25.0, 27.0);
    }
}
