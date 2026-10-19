package com.fleetpilot.vehicle.unit;

import static com.fleetpilot.vehicle.unit.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.vehicle.domain.model.Company;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import com.fleetpilot.vehicle.domain.model.VehicleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Vehicle")
class VehicleTest {

    private final Company company = Fixtures.company("Néolia Énergies");

    @Nested
    @DisplayName("status")
    class Status {

        @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
        @CsvSource({
            "ACTIVE, IN_REPAIR, true",
            "ACTIVE, IMMOBILIZED, true",
            "ACTIVE, DISPOSED, true",
            "IN_REPAIR, ACTIVE, true",
            "AWAITING_DELIVERY, ACTIVE, true",
            "AWAITING_DELIVERY, IN_REPAIR, false",
            "DISPOSED, ACTIVE, false",
            "ACTIVE, AWAITING_DELIVERY, false"
        })
        void follows_the_lifecycle(VehicleStatus from, VehicleStatus to, boolean allowed) {
            assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        }

        @Test
        void a_forbidden_transition_is_refused_with_a_clear_code() {
            Vehicle vehicle = Fixtures.vehicle(company);
            vehicle.changeStatus(VehicleStatus.DISPOSED);

            assertThatThrownBy(() -> vehicle.changeStatus(VehicleStatus.ACTIVE))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "INVALID_STATUS_TRANSITION");
        }

        @Test
        void changing_to_the_same_status_is_a_no_op() {
            Vehicle vehicle = Fixtures.vehicle(company);
            vehicle.changeStatus(VehicleStatus.ACTIVE);
            assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ACTIVE);
        }

        @Test
        void only_vehicles_in_service_can_be_assigned() {
            Vehicle vehicle = Fixtures.vehicle(company);
            assertThat(vehicle.isAssignable()).isTrue();
            vehicle.changeStatus(VehicleStatus.IN_REPAIR);
            assertThat(vehicle.isAssignable()).isFalse();
        }
    }

    @Nested
    @DisplayName("odometer")
    class Odometer {

        @Test
        void a_newer_reading_becomes_the_current_mileage() {
            Vehicle vehicle = Fixtures.vehicle(company);

            boolean applied = vehicle.recordMileage(15_420, NOW);

            assertThat(applied).isTrue();
            assertThat(vehicle.getMileage()).isEqualTo(15_420);
            assertThat(vehicle.getMileageReadAt()).isEqualTo(NOW);
        }

        @Test
        void the_odometer_never_goes_backwards() {
            Vehicle vehicle = Fixtures.vehicle(company);

            assertThatThrownBy(() -> vehicle.recordMileage(14_000, NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "MILEAGE_DECREASE");
        }

        @Test
        void an_older_reading_is_kept_for_history_without_changing_the_vehicle() {
            Vehicle vehicle = Fixtures.vehicle(company);

            boolean applied = vehicle.recordMileage(9_000, NOW.minusSeconds(30 * 86_400));

            assertThat(applied).isFalse();
            assertThat(vehicle.getMileage()).isEqualTo(15_000);
        }

        @Test
        void a_disposed_vehicle_cannot_receive_readings() {
            Vehicle vehicle = Fixtures.vehicle(company);
            vehicle.changeStatus(VehicleStatus.DISPOSED);

            assertThatThrownBy(() -> vehicle.recordMileage(16_000, NOW))
                    .hasFieldOrPropertyWithValue("code", "VEHICLE_DISPOSED");
        }
    }

    @Test
    void electric_vehicles_have_the_crit_air_e_sticker_and_no_co2() {
        assertThatThrownBy(() -> new Vehicle.Specification(VehicleCategory.CAR, "Tesla", "Model 3", null, Energy.ELECTRIC,
                1, 0, 3, LocalDate.of(2025, 1, 1)))
                .hasFieldOrPropertyWithValue("code", "ZERO_EMISSION_INCONSISTENT");
    }

    @Test
    void a_vehicle_cannot_be_based_at_a_site_of_another_company() {
        Vehicle vehicle = Fixtures.vehicle(company);
        Company other = Fixtures.company("Transports Calanques Logistique");

        assertThatThrownBy(() -> vehicle.relocate(Fixtures.site(other), null))
                .hasFieldOrPropertyWithValue("code", "SITE_OF_ANOTHER_COMPANY");
    }
}
