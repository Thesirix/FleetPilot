package com.fleetpilot.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fleetpilot.common.telemetry.TelemetryMessages.FaultReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.MileageReport;
import com.fleetpilot.common.telemetry.TelemetryMessages.PositionReport;
import com.fleetpilot.simulator.engine.FleetSelection;
import com.fleetpilot.simulator.engine.FleetSelection.FleetVehicle;
import com.fleetpilot.simulator.engine.Simulation;
import com.fleetpilot.simulator.engine.TelemetrySink;
import com.fleetpilot.simulator.engine.VehicleProfile;
import com.fleetpilot.simulator.engine.VehicleProfile.Behaviour;
import com.fleetpilot.simulator.road.RoadNetwork;
import com.fleetpilot.simulator.road.Route;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Vehicle simulator")
class SimulatorTest {

    static final String SITE = "Marseille - Arenc";

    /** About 10 km due north of the site, in three straight segments, a fast road (90 km/h on average). */
    Route northbound() {
        return new Route(SITE, "Nord", List.of(new double[] {43.30, 5.37}, new double[] {43.33, 5.37},
                new double[] {43.36, 5.37}, new double[] {43.39, 5.37}), 400);
    }

    RoadNetwork network() {
        return new RoadNetwork(List.of(northbound()), Map.of(SITE, new double[] {43.30, 5.37}));
    }

    static final class Recorder implements TelemetrySink {
        final List<PositionReport> positions = new ArrayList<>();
        final List<MileageReport> mileages = new ArrayList<>();
        final List<FaultReport> faults = new ArrayList<>();

        @Override
        public void position(PositionReport report) {
            positions.add(report);
        }

        @Override
        public void mileage(MileageReport report) {
            mileages.add(report);
        }

        @Override
        public void fault(FaultReport report) {
            faults.add(report);
        }
    }

    /** A clock that the test moves forward. */
    static final class ManualClock extends Clock {
        private Instant now = Instant.parse("2026-10-14T08:00:00Z");

        void advance(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Nested
    @DisplayName("roads")
    class Roads {

        @Test
        void a_route_measures_its_length_and_interpolates_positions() {
            Route route = northbound();
            assertThat(route.length()).isCloseTo(10_007, within(30.0));
            assertThat(route.averageSpeedKmh()).isCloseTo(90.0, within(0.5));
            Route.Point middle = route.at(route.length() / 2);
            assertThat(middle.latitude()).isCloseTo(43.345, within(0.001));
            assertThat(middle.heading()).isZero();
            assertThat(route.at(-5).latitude()).isEqualTo(43.30);
            assertThat(route.at(1e9).latitude()).isEqualTo(43.39);
        }

        @Test
        void the_way_back_is_the_same_road_reversed() {
            Route back = northbound().reversed();
            assertThat(back.from()).isEqualTo("Nord");
            assertThat(back.to()).isEqualTo(SITE);
            assertThat(back.at(0).latitude()).isEqualTo(43.39);
            assertThat(back.at(10).heading()).isEqualTo(180);
            assertThat(back.averageSpeedKmh()).isCloseTo(90.0, within(0.5));
        }

        @Test
        void the_shipped_network_covers_the_sites_of_the_demo_companies() {
            RoadNetwork shipped = RoadNetwork.load(JsonMapper.builder().build(), "/simulator/routes.json");
            assertThat(shipped.sites()).contains(SITE, "Paris - La Défense", "Vitrolles - Plateforme logistique");
            assertThat(shipped.routesFrom(SITE)).isNotEmpty();
            assertThat(shipped.routeCount()).isGreaterThan(100);
        }
    }

    @Nested
    @DisplayName("fleet")
    class Fleet {

        @Test
        void only_vehicles_in_service_at_a_known_site_are_connected_spread_over_the_fleet() {
            List<FleetVehicle> fleet = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                fleet.add(new FleetVehicle(UUID.nameUUIDFromBytes(("v" + i).getBytes()), UUID.randomUUID(), "AB-" + i,
                        "CAR", "DIESEL", i % 10 == 0 ? "IN_REPAIR" : "ACTIVE", i % 7 == 0 ? "Ailleurs" : SITE, 10_000));
            }
            List<VehicleProfile> profiles = FleetSelection.select(fleet, Set.of(SITE), 30);
            assertThat(profiles).hasSize(30);
            assertThat(profiles).extracting(VehicleProfile::site).containsOnly(SITE);
            assertThat(profiles).extracting(VehicleProfile::behaviour).contains(Behaviour.SPEEDER, Behaviour.IDLER);
            assertThat(FleetSelection.select(fleet, Set.of(SITE), 30)).isEqualTo(profiles);
            assertThat(FleetSelection.select(fleet, Set.of("Lune"), 30)).isEmpty();
        }
    }

    @Nested
    @DisplayName("driving")
    class Driving {

        private Recorder drive(VehicleProfile profile, int minutes) {
            ManualClock clock = new ManualClock();
            Recorder recorder = new Recorder();
            Simulation simulation = new Simulation(network(), recorder, clock);
            simulation.start(List.of(profile));
            for (int i = 0; i < minutes * 30; i++) {
                clock.advance(2);
                simulation.tick();
            }
            return recorder;
        }

        private static VehicleProfile profile(String energy, Behaviour behaviour) {
            return new VehicleProfile(UUID.nameUUIDFromBytes(("car-" + behaviour + energy).getBytes()), UUID.randomUUID(),
                    "GH-482-KM", "CAR", energy, SITE, 42_000, behaviour);
        }

        @Test
        void a_vehicle_drives_on_the_road_and_counts_its_kilometres() {
            Recorder recorder = drive(profile("DIESEL", Behaviour.CALM), 240);
            List<PositionReport> moving = recorder.positions.stream().filter(PositionReport::ignition).toList();
            assertThat(moving).isNotEmpty();
            assertThat(moving).allSatisfy(p -> {
                assertThat(p.longitude()).isCloseTo(5.37, within(0.0001));
                assertThat(p.latitude()).isBetween(43.29999, 43.39001);
            });
            assertThat(recorder.positions).anySatisfy(p -> assertThat(p.ignition()).isFalse());
            assertThat(recorder.mileages.getLast().odometerKm()).isGreaterThan(42_010);
            for (int i = 1; i < recorder.mileages.size(); i++) {
                assertThat(recorder.mileages.get(i).odometerKm()).isGreaterThanOrEqualTo(
                        recorder.mileages.get(i - 1).odometerKm());
            }
        }

        @Test
        void a_speeding_driver_goes_far_above_the_limit_on_fast_roads() {
            Recorder recorder = drive(profile("PETROL", Behaviour.SPEEDER), 120);
            assertThat(recorder.positions).anySatisfy(p -> assertThat(p.speedKmh()).isGreaterThan(145));
        }

        @Test
        void an_electric_vehicle_reports_its_battery() {
            Recorder recorder = drive(profile("ELECTRIC", Behaviour.CALM), 60);
            assertThat(recorder.positions).allSatisfy(p -> assertThat(p.batteryPercent()).isBetween(0, 100));
        }

        @Test
        void a_faulty_vehicle_reports_a_serious_trouble_code_and_a_fault_can_be_injected() {
            Recorder recorder = drive(profile("DIESEL", Behaviour.FAULTY), 600);
            assertThat(recorder.faults).isNotEmpty();
            assertThat(recorder.faults.getFirst().code()).isEqualTo("P0300");

            ManualClock clock = new ManualClock();
            Recorder injected = new Recorder();
            Simulation simulation = new Simulation(network(), injected, clock);
            simulation.start(List.of(profile("DIESEL", Behaviour.CALM)));
            assertThat(simulation.injectFault("GH-482-KM", "P0217")).isPresent();
            assertThat(injected.faults.getFirst().description()).contains("Surchauffe");
            assertThat(simulation.injectFault("ZZ-000-ZZ", "P0217")).isEmpty();
            assertThat(simulation.status().vehicles()).isOne();
        }

        @Test
        void an_electric_vehicle_never_reports_a_combustion_engine_fault() {
            Recorder recorder = drive(profile("ELECTRIC", Behaviour.FAULTY), 600);
            // the serious fault of an electric drive train: high voltage insulation, not a misfire
            assertThat(recorder.faults).isNotEmpty().allSatisfy(f -> assertThat(f.code()).isEqualTo("P0AA6"));

            Recorder injected = new Recorder();
            Simulation simulation = new Simulation(network(), injected, new ManualClock());
            simulation.start(List.of(profile("ELECTRIC", Behaviour.CALM)));
            assertThat(simulation.injectFault("GH-482-KM", null)).hasValueSatisfying(f ->
                    assertThat(f.code()).isEqualTo("P0AA6"));
        }
    }
}
