package com.fleetpilot.vehicle.unit;

import static com.fleetpilot.vehicle.unit.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;

import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.Driver;
import com.fleetpilot.vehicle.domain.model.LicenseCategory;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Driver licence")
class DriverTest {

    private final Company company = Fixtures.company("Néolia Énergies");

    @ParameterizedTest(name = "licence {0} can drive a {1}: {2}")
    @CsvSource({
        "B, CAR, true",
        "B, VAN, true",
        "B, TRUCK, false",
        "C, TRUCK, true",
        "CE, TRUCK, true",
        "C1, TRUCK, false",
        "B, MACHINE, true"
    })
    void the_licence_must_cover_the_vehicle_category(LicenseCategory licence, VehicleCategory category, boolean allowed) {
        Driver driver = Fixtures.driver(company, licence);
        assertThat(driver.canDrive(category, TODAY)).isEqualTo(allowed);
    }

    @Test
    void an_expired_licence_allows_nothing_but_machinery() {
        Driver driver = Fixtures.driver(company, TODAY.minusDays(1), LicenseCategory.B, LicenseCategory.C);

        assertThat(driver.canDrive(VehicleCategory.CAR, TODAY)).isFalse();
        assertThat(driver.canDrive(VehicleCategory.TRUCK, TODAY)).isFalse();
        assertThat(driver.canDrive(VehicleCategory.CAR, TODAY.minusDays(2))).isTrue();
    }

    @Test
    void a_licence_cannot_expire_before_it_is_issued() {
        assertThatThrownBy(() -> new Driver.License("123", Set.of(LicenseCategory.B), LocalDate.of(2020, 1, 1),
                LocalDate.of(2019, 1, 1)))
                .hasFieldOrPropertyWithValue("code", "LICENSE_DATES_INVALID");
    }

    @Test
    void the_full_name_is_first_then_last_name() {
        assertThat(Fixtures.driver(company, LicenseCategory.B).getFullName()).isEqualTo("Camille Martin");
    }
}
