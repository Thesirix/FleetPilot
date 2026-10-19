package com.fleetpilot.vehicle.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.LeaseContract;
import com.fleetpilot.vehicle.domain.model.LeaseContract.MileageProjection;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Long-term lease contract")
class LeaseContractTest {

    private static final LocalDate START = LocalDate.of(2025, 1, 1);

    private LeaseContract contract(int contractualMileage) {
        Vehicle vehicle = Fixtures.vehicle(Fixtures.company("Néolia Énergies"), Fixtures.car(Energy.ELECTRIC), 0);
        return new LeaseContract(UUID.randomUUID(), vehicle, new LeaseContract.Terms("Ayvens", "AYV-2025-000001", START,
                36, contractualMileage, new BigDecimal("449.9"), true, true, false));
    }

    @Test
    void ends_after_its_duration_and_costs_rent_times_months() {
        LeaseContract contract = contract(90_000);

        assertThat(contract.getEndDate()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(contract.getMonthlyRent()).isEqualByComparingTo("449.90");
        assertThat(contract.getTotalRent()).isEqualByComparingTo("16196.40");
    }

    @Test
    void projects_the_mileage_at_the_end_from_the_real_pace() {
        LeaseContract contract = contract(90_000);
        LocalDate halfway = START.plusDays(547);

        MileageProjection projection = contract.projectMileage(54_700, halfway);

        assertThat(projection.dailyAverageKm()).isEqualTo(100.0);
        assertThat(projection.projectedMileage()).isEqualTo(109_500);
        assertThat(projection.projectedOverrunKm()).isEqualTo(19_500);
        assertThat(projection.overrunRisk()).isTrue();
        assertThat(contract.needsOverrunAlert(projection)).isTrue();
        contract.markOverrunAlerted(halfway);
        assertThat(contract.needsOverrunAlert(projection)).isFalse();
    }

    @Test
    void a_small_overrun_within_tolerance_is_not_a_risk() {
        LeaseContract contract = contract(90_000);

        MileageProjection projection = contract.projectMileage(46_500, START.plusDays(547));

        assertThat(projection.projectedOverrunKm()).isPositive().isLessThan(4_500);
        assertThat(projection.overrunRisk()).isFalse();
    }

    @Test
    void terms_are_checked() {
        assertThatThrownBy(() -> new LeaseContract.Terms("Arval", "X", START, 3, 10_000, BigDecimal.TEN, false, false, false))
                .hasFieldOrPropertyWithValue("code", "CONTRACT_DURATION_INVALID");
        assertThatThrownBy(() -> new LeaseContract.Terms("Arval", "X", START, 36, 0, BigDecimal.TEN, false, false, false))
                .hasFieldOrPropertyWithValue("code", "CONTRACT_TERMS_INVALID");
    }

    @Test
    void a_contract_ends_once_and_not_before_it_starts() {
        LeaseContract contract = contract(90_000);
        assertThatThrownBy(() -> contract.end(START.minusDays(1)))
                .hasFieldOrPropertyWithValue("code", "CONTRACT_END_BEFORE_START");

        contract.end(START.plusYears(2));

        assertThat(contract.getStatus()).isEqualTo(LeaseContract.Status.ENDED);
        assertThatThrownBy(() -> contract.end(START.plusYears(2)))
                .hasFieldOrPropertyWithValue("code", "CONTRACT_ALREADY_ENDED");
    }
}
