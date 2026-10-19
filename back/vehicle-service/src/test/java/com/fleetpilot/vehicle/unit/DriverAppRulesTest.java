package com.fleetpilot.vehicle.unit;

import static com.fleetpilot.vehicle.unit.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.common.error.BusinessRuleException;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.FuelEntry;
import com.fleetpilot.vehicle.domain.model.FuelEntry.Kind;
import com.fleetpilot.vehicle.domain.model.FuelEntry.Payment;
import com.fleetpilot.vehicle.domain.model.Inspection;
import com.fleetpilot.vehicle.domain.model.Inspection.Check;
import com.fleetpilot.vehicle.domain.model.Inspection.Condition;
import com.fleetpilot.vehicle.domain.model.Inspection.Item;
import com.fleetpilot.vehicle.domain.model.Photo;
import com.fleetpilot.vehicle.domain.model.Vehicle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("What drivers send from the app: fill-ups, inspections, photos")
class DriverAppRulesTest {

    private static final UUID DRIVER = UUID.randomUUID();

    private static FuelEntry.Receipt receipt(Kind kind, String quantity, String amount) {
        return new FuelEntry.Receipt(NOW.minus(Duration.ofHours(2)), kind, new BigDecimal(quantity),
                new BigDecimal(amount), 15_120, " TotalEnergies Lyon Gerland ", Payment.FUEL_CARD);
    }

    private static Vehicle vehicle(Energy energy) {
        return Fixtures.vehicle(Fixtures.company("Néolia"), Fixtures.car(energy), 15_000);
    }

    @Nested
    @DisplayName("Fill-ups and charges")
    class FillUps {

        @ParameterizedTest(name = "{0} takes {1}: {2}")
        @CsvSource({
            "DIESEL, FUEL, true", "DIESEL, CHARGE, false", "ELECTRIC, CHARGE, true", "ELECTRIC, FUEL, false",
            "PLUG_IN_HYBRID, FUEL, true", "PLUG_IN_HYBRID, CHARGE, true", "HYBRID, CHARGE, false"
        })
        void the_kind_of_entry_follows_the_energy_of_the_vehicle(Energy energy, Kind kind, boolean accepted) {
            assertThat(FuelEntry.accepts(energy, kind)).isEqualTo(accepted);
        }

        @Test
        void a_receipt_is_recorded_with_its_station_trimmed() {
            FuelEntry entry = new FuelEntry(UUID.randomUUID(), vehicle(Energy.DIESEL), DRIVER,
                    receipt(Kind.FUEL, "42.17", "76.33"), NOW);
            assertThat(entry.getStation()).isEqualTo("TotalEnergies Lyon Gerland");
            assertThat(entry.getMileage()).isEqualTo(15_120);
        }

        @Test
        void an_electric_vehicle_is_charged_not_filled_up() {
            assertThatThrownBy(() -> new FuelEntry(UUID.randomUUID(), vehicle(Energy.ELECTRIC), DRIVER,
                    receipt(Kind.FUEL, "40", "70"), NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("is electric");
        }

        @ParameterizedTest(name = "{0} of {1} for {2} euros is refused")
        @CsvSource({"FUEL, 4.217, 76.33", "FUEL, 42.17, 7.63", "CHARGE, 10, 25"})
        void an_implausible_price_reveals_a_typing_error(Kind kind, String quantity, String amount) {
            Energy energy = kind == Kind.CHARGE ? Energy.ELECTRIC : Energy.PETROL;
            assertThatThrownBy(() -> new FuelEntry(UUID.randomUUID(), vehicle(energy), DRIVER,
                    receipt(kind, quantity, amount), NOW))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not plausible");
        }

        @Test
        void charging_at_the_depot_can_be_free() {
            FuelEntry entry = new FuelEntry(UUID.randomUUID(), vehicle(Energy.ELECTRIC), DRIVER,
                    receipt(Kind.CHARGE, "38.5", "0"), NOW);
            assertThat(entry.getAmount()).isZero();
        }

        @Test
        void a_fill_up_is_neither_in_the_future_nor_older_than_90_days() {
            Vehicle vehicle = vehicle(Energy.DIESEL);
            assertThatThrownBy(() -> new FuelEntry(UUID.randomUUID(), vehicle, DRIVER, new FuelEntry.Receipt(
                    NOW.plus(Duration.ofHours(1)), Kind.FUEL, BigDecimal.TEN, new BigDecimal("18"), 15_000, null,
                    Payment.PERSONAL), NOW))
                    .hasMessageContaining("future");
            assertThatThrownBy(() -> new FuelEntry(UUID.randomUUID(), vehicle, DRIVER, new FuelEntry.Receipt(
                    NOW.minus(Duration.ofDays(91)), Kind.FUEL, BigDecimal.TEN, new BigDecimal("18"), 15_000, null,
                    Payment.PERSONAL), NOW))
                    .hasMessageContaining("90 days");
        }
    }

    @Nested
    @DisplayName("Inspections")
    class Inspections {

        private List<Check> allOk() {
            return new ArrayList<>(Arrays.stream(Item.values()).map(item -> new Check(item, Condition.OK, null)).toList());
        }

        private Inspection inspection(List<Check> checks) {
            return new Inspection(UUID.randomUUID(), UUID.randomUUID(), DRIVER, Inspection.Kind.PICKUP,
                    NOW.minus(Duration.ofMinutes(5)), 15_000, 80, "  ", checks, NOW);
        }

        @Test
        void every_point_of_the_checklist_is_answered() {
            List<Check> checks = allOk();
            checks.removeIf(check -> check.getItem() == Item.TYRES);
            assertThatThrownBy(() -> inspection(checks))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("TYRES");
        }

        @Test
        void a_point_answered_twice_is_refused() {
            List<Check> checks = allOk();
            checks.add(new Check(Item.LIGHTS, Condition.DAMAGED, "Feu arrière gauche"));
            assertThatThrownBy(() -> inspection(checks)).hasMessageContaining("twice");
        }

        @Test
        void a_damaged_point_says_what_is_wrong() {
            List<Check> checks = allOk();
            checks.replaceAll(check -> check.getItem() == Item.BODYWORK ? new Check(Item.BODYWORK, Condition.DAMAGED, " ")
                    : check);
            assertThatThrownBy(() -> inspection(checks)).hasMessageContaining("BODYWORK");
        }

        @Test
        void the_defects_are_what_the_fleet_manager_looks_at() {
            List<Check> checks = allOk();
            checks.replaceAll(check -> switch (check.getItem()) {
                case WINDSCREEN -> new Check(Item.WINDSCREEN, Condition.DAMAGED, "Impact côté passager");
                case SAFETY_KIT -> new Check(Item.SAFETY_KIT, Condition.MISSING, "Gilet absent");
                default -> check;
            });
            Inspection inspection = inspection(checks);
            assertThat(inspection.defects()).extracting(Check::getItem).containsExactly(Item.WINDSCREEN, Item.SAFETY_KIT);
            assertThat(inspection.getRemarks()).isNull();
            assertThat(inspection.getChecks()).hasSize(Item.values().length);
        }
    }

    @Nested
    @DisplayName("Photos")
    class Photos {

        @Test
        void a_photo_is_an_image_of_at_most_2_mb() {
            UUID owner = UUID.randomUUID();
            assertThatThrownBy(() -> new Photo(UUID.randomUUID(), Photo.Owner.INCIDENT, owner, owner, "application/pdf",
                    new byte[100], NOW)).hasMessageContaining("JPEG");
            assertThatThrownBy(() -> new Photo(UUID.randomUUID(), Photo.Owner.INCIDENT, owner, owner, "image/jpeg",
                    new byte[Photo.MAX_BYTES + 1], NOW)).hasMessageContaining("2 MB");
            assertThat(new Photo(UUID.randomUUID(), Photo.Owner.INCIDENT, owner, owner, "image/jpeg", new byte[300_000], NOW)
                    .getSizeBytes()).isEqualTo(300_000);
        }
    }
}
