package com.fleetpilot.vehicle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDate;

import com.fleetpilot.vehicle.domain.model.Acquisition;
import com.fleetpilot.vehicle.domain.model.Energy;
import com.fleetpilot.vehicle.domain.model.VehicleCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CSV import: values as typed by French users")
class VehicleImportParsingTest {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({"VL, CAR", "vu, VAN", "PL, TRUCK", "Engin, MACHINE", "van, VAN"})
    void categories(String raw, VehicleCategory expected) {
        assertThat(VehicleImportService.category(raw)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({"Électrique, ELECTRIC", "electrique, ELECTRIC", "Gazole, DIESEL", "Hybride rechargeable, PLUG_IN_HYBRID",
        "GNV, CNG", "PETROL, PETROL", "plug_in_hybrid, PLUG_IN_HYBRID"})
    void energies(String raw, Energy expected) {
        assertThat(VehicleImportService.energy(raw)).isEqualTo(expected);
    }

    @Test
    void acquisition_crit_air_and_dates() {
        assertThat(VehicleImportService.acquisition("LLD")).isEqualTo(Acquisition.LEASE);
        assertThat(VehicleImportService.acquisition("Achat")).isEqualTo(Acquisition.OWNED);
        assertThat(VehicleImportService.critAir("E")).isZero();
        assertThat(VehicleImportService.critAir("2")).isEqualTo(2);
        assertThat(VehicleImportService.date("15/03/2024")).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(VehicleImportService.date("2024-03-15")).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThatIllegalArgumentException().isThrownBy(() -> VehicleImportService.date("15.03.2024"));
        assertThatIllegalArgumentException().isThrownBy(() -> VehicleImportService.category("Moto"));
    }
}
