package com.fleetpilot.vehicle.unit;

import static com.fleetpilot.vehicle.unit.Fixtures.TODAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.vehicle.domain.model.DeadlinePolicy;
import com.fleetpilot.vehicle.domain.model.DeadlinePolicy.Due;
import com.fleetpilot.vehicle.domain.model.DeadlineType;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Deadline policy (French rules)")
class DeadlinePolicyTest {

    private static Map<DeadlineType, Due> schedule(VehicleCategory category, Energy energy, LocalDate firstRegistration,
            int mileage) {
        List<Due> dues = DeadlinePolicy.initialSchedule(category, energy, firstRegistration, mileage, TODAY);
        return dues.stream().collect(Collectors.toMap(Due::type, Function.identity()));
    }

    @Test
    void a_new_car_gets_its_first_technical_inspection_after_four_years() {
        Map<DeadlineType, Due> dues = schedule(VehicleCategory.CAR, Energy.PETROL, LocalDate.of(2025, 3, 10), 18_300);

        assertThat(dues.get(DeadlineType.TECHNICAL_INSPECTION).dueDate()).isEqualTo(LocalDate.of(2029, 3, 10));
        assertThat(dues.get(DeadlineType.SERVICE).dueDate()).isEqualTo(LocalDate.of(2027, 3, 10));
        assertThat(dues.get(DeadlineType.SERVICE).dueMileage()).isEqualTo(20_000);
        assertThat(dues.get(DeadlineType.TYRES).dueMileage()).isEqualTo(40_000);
        assertThat(dues).doesNotContainKey(DeadlineType.TACHOGRAPH);
    }

    @Test
    void an_older_car_is_inspected_every_two_years_after_the_first_one() {
        Map<DeadlineType, Due> dues = schedule(VehicleCategory.CAR, Energy.DIESEL, LocalDate.of(2020, 11, 2), 101_000);

        // 2024-11-02 first inspection, then 2026-11-02
        assertThat(dues.get(DeadlineType.TECHNICAL_INSPECTION).dueDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(dues.get(DeadlineType.TYRES).dueMileage()).isEqualTo(120_000);
    }

    @Test
    void electric_cars_need_less_maintenance() {
        Map<DeadlineType, Due> dues = schedule(VehicleCategory.CAR, Energy.ELECTRIC, LocalDate.of(2025, 3, 10), 18_300);

        assertThat(dues.get(DeadlineType.SERVICE).dueDate()).isEqualTo(LocalDate.of(2027, 3, 10));
        assertThat(dues.get(DeadlineType.SERVICE).dueMileage()).isEqualTo(30_000);
    }

    @Test
    void trucks_are_inspected_every_year_and_get_a_tachograph_calibration() {
        Map<DeadlineType, Due> dues = schedule(VehicleCategory.TRUCK, Energy.DIESEL, LocalDate.of(2024, 2, 1), 150_000);

        assertThat(dues.get(DeadlineType.TECHNICAL_INSPECTION).dueDate()).isEqualTo(LocalDate.of(2027, 2, 1));
        assertThat(dues.get(DeadlineType.TACHOGRAPH).dueDate()).isEqualTo(LocalDate.of(2028, 2, 1));
        assertThat(dues.get(DeadlineType.SERVICE).dueMileage()).isEqualTo(180_000);
        assertThat(dues.get(DeadlineType.TYRES).dueMileage()).isEqualTo(200_000);
    }

    @Test
    void machinery_has_no_inspection_nor_tyre_deadline() {
        Map<DeadlineType, Due> dues = schedule(VehicleCategory.MACHINE, Energy.DIESEL, LocalDate.of(2022, 5, 1), 3_000);

        assertThat(dues).containsOnlyKeys(DeadlineType.SERVICE);
        assertThat(dues.get(DeadlineType.SERVICE).dueMileage()).isNull();
    }

    @Test
    void the_next_occurrence_starts_from_the_completion() {
        assertThat(DeadlinePolicy.next(DeadlineType.TECHNICAL_INSPECTION, VehicleCategory.VAN, Energy.DIESEL, TODAY, 80_000))
                .contains(new Due(DeadlineType.TECHNICAL_INSPECTION, TODAY.plusYears(2), null));
        assertThat(DeadlinePolicy.next(DeadlineType.SERVICE, VehicleCategory.VAN, Energy.DIESEL, TODAY, 80_000))
                .contains(new Due(DeadlineType.SERVICE, TODAY.plusYears(1), 110_000));
        assertThat(DeadlinePolicy.next(DeadlineType.TYRES, VehicleCategory.CAR, Energy.PETROL, TODAY, 52_300))
                .contains(new Due(DeadlineType.TYRES, null, 92_300));
        assertThat(DeadlinePolicy.next(DeadlineType.CONTRACT_END, VehicleCategory.CAR, Energy.PETROL, TODAY, 1)).isEmpty();
    }
}
