package com.fleetpilot.vehicle.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.common.util.RegistrationNumbers;
import com.fleetpilot.common.util.Vins;
import com.fleetpilot.vehicle.infrastructure.demo.DemoDataset.VehicleRow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Demo data set")
class DemoDataGeneratorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static DemoDataset data;

    @BeforeAll
    static void generate() {
        data = new DemoDataGenerator(DemoDataGenerator.DEFAULT_SEED, TODAY).generate();
    }

    @Test
    void is_identical_at_every_installation() {
        DemoDataset again = new DemoDataGenerator(DemoDataGenerator.DEFAULT_SEED, TODAY).generate();

        assertThat(again.vehicles).isEqualTo(data.vehicles);
        assertThat(again.drivers).isEqualTo(data.drivers);
        assertThat(again.contracts).isEqualTo(data.contracts);
        assertThat(again.deadlines).isEqualTo(data.deadlines);
        assertThat(again.incidents).isEqualTo(data.incidents);
    }

    @Test
    void has_the_announced_size() {
        assertThat(data.companies).hasSize(3);
        assertThat(data.vehicles).hasSize(1_500);
        assertThat(data.drivers).hasSize(1_200);
        assertThat(data.contracts).hasSizeGreaterThan(1_000);
        assertThat(data.incidents).hasSizeBetween(250, 400);
    }

    @Test
    void registrations_and_vins_are_valid_and_unique() {
        assertThat(data.vehicles).allSatisfy(v -> {
            assertThat(RegistrationNumbers.normalize(v.registration())).contains(v.registration());
            assertThat(Vins.isValid(v.vin())).as(v.vin()).isTrue();
        });
        assertThat(data.vehicles.stream().map(VehicleRow::registration).distinct()).hasSize(1_500);
        assertThat(data.vehicles.stream().map(VehicleRow::vin).distinct()).hasSize(1_500);
        assertThat(data.drivers.stream().map(DemoDataset.DriverRow::email).distinct()).hasSize(1_200);
    }

    @Test
    void crit_air_stickers_follow_the_energy_and_age() {
        assertThat(data.vehicles).allSatisfy(v -> {
            switch (v.energy()) {
                case "ELECTRIC", "HYDROGEN" -> assertThat(v.critAir()).isZero();
                case "DIESEL" -> assertThat(v.critAir()).isBetween(2, 4);
                default -> assertThat(v.critAir()).isEqualTo(1);
            }
        });
        assertThat(data.vehicles).anyMatch(v -> v.critAir() == 3);
    }

    @Test
    void every_reference_points_to_an_existing_row() {
        Map<UUID, VehicleRow> vehicles = data.vehicles.stream()
                .collect(Collectors.toMap(VehicleRow::id, Function.identity()));
        assertThat(data.contracts).allSatisfy(c -> assertThat(vehicles).containsKey(c.vehicleId()));
        assertThat(data.deadlines).allSatisfy(d -> assertThat(vehicles).containsKey(d.vehicleId()));
        assertThat(data.assignments).allSatisfy(a -> {
            assertThat(vehicles).containsKey(a.vehicleId());
            assertThat(a.endDate() == null || !a.endDate().isBefore(a.startDate())).isTrue();
        });
        assertThat(data.contracts).allSatisfy(c -> assertThat(c.endedOn() == null || !c.endedOn().isBefore(c.startDate()))
                .isTrue());
    }

    @Test
    void the_drivers_with_a_demo_login_always_have_a_vehicle() {
        for (String name : new String[] {"Camille Martin", "Jean Dupont", "Julie Faure"}) {
            DemoDataset.DriverRow driver = data.drivers.stream()
                    .filter(d -> (d.firstName() + " " + d.lastName()).equals(name)).findFirst().orElseThrow();
            assertThat(driver.companyId()).isEqualTo(DemoDataGenerator.uuid("company", "NEO"));
            assertThat(driver.active()).isTrue();
            assertThat(data.assignments).as(name).anyMatch(a -> a.driverId().equals(driver.id()) && a.endDate() == null);
            // Based in Marseille, with the vehicle they drive
            UUID marseille = data.sites.stream().filter(site -> site.name().equals("Marseille - Arenc")).findFirst()
                    .orElseThrow().id();
            assertThat(driver.siteId()).as(name).isEqualTo(marseille);
            data.assignments.stream().filter(a -> a.driverId().equals(driver.id()) && a.endDate() == null)
                    .forEach(a -> assertThat(data.vehicles).filteredOn(v -> v.id().equals(a.vehicleId()))
                            .singleElement().extracting(DemoDataset.VehicleRow::siteId).isEqualTo(marseille));
        }
        assertThat(DemoDataGenerator.uuid("driver", "NEO0")).hasToString("87162d7c-e396-30cd-b37c-a6278869bd78");
    }

    @Test
    void the_demo_has_something_to_do_for_the_fleet_manager() {
        assertThat(data.deadlines).anyMatch(d -> "OPEN".equals(d.status()) && d.dueDate() != null && d.dueDate().isBefore(TODAY));
        assertThat(data.incidents).anyMatch(i -> "DECLARED".equals(i.status()));
        assertThat(data.vehicles).anyMatch(v -> "IN_REPAIR".equals(v.status()));
        assertThat(data.drivers.size() - data.assignments.stream().filter(a -> a.endDate() == null).count())
                .as("drivers without a vehicle").isGreaterThan(50);
    }
}
