package com.fleetpilot.energy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.fleetpilot.energy.domain.Transition.Assessment;
import com.fleetpilot.energy.domain.Transition.Eligibility;
import com.fleetpilot.energy.domain.Transition.EvModel;
import com.fleetpilot.energy.domain.Transition.Prices;
import com.fleetpilot.energy.domain.Transition.Usage;
import com.fleetpilot.energy.domain.Transition.VehicleFacts;
import com.fleetpilot.energy.domain.Transition.ZfeRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Electric eligibility of a vehicle from its real usage")
class TransitionAnalysisTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final List<EvModel> CATALOG = List.of(
            model("R5-ETECH", "CAR", "CITY", 410, "15.2", "389"),
            model("E-208", "CAR", "CITY", 410, "15.5", "409"),
            model("MEGANE-ET", "CAR", "COMPACT", 450, "16.1", "469"),
            model("EV3", "CAR", "COMPACT", 600, "15.9", "499"),
            model("ENYAQ85", "CAR", "LARGE", 581, "16.8", "579"),
            model("KANGOO-ET", "VAN", "SMALL", 300, "20.0", "459"),
            model("TRAFIC-ET", "VAN", "MEDIUM", 297, "24.0", "589"),
            model("MASTER-ET", "VAN", "LARGE", 460, "25.0", "779"),
            model("ETECH-D", "TRUCK", "TRUCK", 280, "110.0", "2900"));
    static final List<ZfeRule> ZONES = List.of(
            new ZfeRule("Marseille", "ZFE Marseille Provence", 2, LocalDate.of(2025, 9, 1), "IN_FORCE"));
    static final Prices PRICES = new Prices(new BigDecimal("1.72"), new BigDecimal("1.81"), new BigDecimal("1.00"),
            new BigDecimal("1.45"), new BigDecimal("0.20"), new BigDecimal("0.45"), new BigDecimal("0.052"));

    private final TransitionAnalysis analysis = new TransitionAnalysis(CATALOG, ZONES, PRICES, TODAY);

    static EvModel model(String code, String category, String segment, int wltp, String kwh, String rent) {
        return new EvModel(code, "Marque", code, category, segment, new BigDecimal("50"), wltp, new BigDecimal(kwh), 100,
                new BigDecimal(rent));
    }

    static VehicleFacts vehicle(String category, String energy, String model, int co2, String city, int critAir,
            LocalDate contractEnd, String rent) {
        return new VehicleFacts(UUID.randomUUID(), UUID.randomUUID(), "Néolia Énergies", "GH-482-KM", "Renault", model,
                category, energy, critAir, co2, city, 84_000, LocalDate.of(2022, 3, 1), contractEnd == null ? "OWNED"
                        : "LEASED", contractEnd, rent == null ? null : new BigDecimal(rent));
    }

    /** {@code days} days driven, the distance of day i given by the function. */
    static Usage usage(int days, java.util.function.IntFunction<Integer> km) {
        List<BigDecimal> distances = new ArrayList<>();
        IntStream.range(0, days).forEach(i -> distances.add(BigDecimal.valueOf(km.apply(i))));
        return new Usage(90, distances);
    }

    @Test
    void a_city_car_driving_short_days_goes_electric_with_the_cheapest_city_model_and_saves_money() {
        Assessment a = analysis.assess(vehicle("CAR", "DIESEL", "Clio V", 118, "Lyon", 2,
                LocalDate.of(2027, 3, 31), "339.00"), usage(60, i -> 30 + i % 50));

        assertThat(a.status()).isEqualTo(Eligibility.ELIGIBLE);
        assertThat(a.dataSource()).isEqualTo("TELEMETRY");
        assertThat(a.evModel().code()).isEqualTo("R5-ETECH");
        assertThat(a.evModel().realisticRangeKm()).isEqualTo(287);
        assertThat(a.daysBeyondRange()).isZero();
        assertThat(a.p95DailyKm()).isEqualByComparingTo("76");
        assertThat(a.requiredRangeKm()).isEqualTo(88);
        assertThat(a.reason()).startsWith("Tous les jours roulés tiennent dans l'autonomie réelle de 287 km");
        // 60 days and 3,070 km in 90 days: 12,451 km a year
        assertThat(a.annualKm()).isEqualTo(12_451);
        assertThat(a.co2CurrentKg()).isEqualTo(1_469);
        assertThat(a.co2EvKg()).isEqualTo(98);
        assertThat(a.annualSaving()).isPositive();
        assertThat(a.renewalDate()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(a.zfeZone()).isNull();
    }

    @Test
    void a_vehicle_is_replaced_by_a_model_of_its_size_or_larger_never_smaller() {
        Assessment largeVan = analysis.assess(vehicle("VAN", "DIESEL", "Master Fourgon", 210, "Lyon", 2, null, null),
                usage(60, i -> 120));
        assertThat(largeVan.evModel().code()).isEqualTo("MASTER-ET");
        Assessment smallVan = analysis.assess(vehicle("VAN", "DIESEL", "Kangoo Van", 140, "Lyon", 2, null, null),
                usage(60, i -> 80));
        assertThat(smallVan.evModel().code()).isEqualTo("KANGOO-ET");
        Assessment largeCar = analysis.assess(vehicle("CAR", "DIESEL", "Skoda Superb", 140, "Lyon", 2, null, null),
                usage(60, i -> 60));
        assertThat(largeCar.evModel().segment()).isEqualTo("LARGE");
    }

    @Test
    void a_few_long_days_need_a_fast_charge_and_frequent_ones_keep_the_vehicle_thermal_for_now() {
        Assessment occasional = analysis.assess(vehicle("CAR", "DIESEL", "Mégane Estate", 125, "Paris", 2, null, null),
                usage(60, i -> i % 30 == 0 ? 360 : 70));
        assertThat(occasional.status()).isEqualTo(Eligibility.ELIGIBLE_WITH_CHARGING);
        assertThat(occasional.evModel().code()).isEqualTo("MEGANE-ET");
        assertThat(occasional.daysBeyondRange()).isEqualTo(2);
        assertThat(occasional.reason()).startsWith("2 jours sur 60 dépassent l'autonomie réelle de 315 km");

        Assessment frequent = analysis.assess(vehicle("VAN", "DIESEL", "Master", 210, "Lyon", 2, null, null),
                usage(60, i -> i % 3 == 0 ? 380 : 120));
        assertThat(frequent.status()).isEqualTo(Eligibility.NOT_YET);
        assertThat(frequent.evModel().code()).isEqualTo("MASTER-ET");
        assertThat(frequent.daysBeyondRange()).isEqualTo(20);
    }

    @Test
    void without_a_connected_box_the_usage_is_estimated_from_the_odometer_and_must_be_confirmed() {
        Assessment a = analysis.assess(vehicle("VAN", "DIESEL", "Kangoo", 140, "Marseille", 3, null, null), null);

        assertThat(a.status()).isEqualTo(Eligibility.TO_CONFIRM);
        assertThat(a.dataSource()).isEqualTo("REGISTER");
        // 84,000 km since March 2022: about 18,600 km a year
        assertThat(a.annualKm()).isBetween(18_000, 19_000);
        assertThat(a.reason()).contains("Estimation d'après le compteur");
        assertThat(a.evModel().code()).isEqualTo("KANGOO-ET");
        // Crit'Air 3 in Marseille: banned since September 2025
        assertThat(a.zfeZone()).isEqualTo("ZFE Marseille Provence");
        assertThat(a.zfeBannedFrom()).isEqualTo(LocalDate.of(2025, 9, 1));
    }

    @Test
    void electric_vehicles_and_site_machinery_are_not_assessed_for_a_switch() {
        assertThat(analysis.assess(vehicle("CAR", "ELECTRIC", "Mégane E-Tech", 0, "Lyon", 0, null, null), null)
                .status()).isEqualTo(Eligibility.ZERO_EMISSION);
        Assessment machine = analysis.assess(vehicle("MACHINE", "DIESEL", "JCB 3CX", 250, "Lyon", 4, null, null),
                usage(30, i -> 12));
        assertThat(machine.status()).isEqualTo(Eligibility.NOT_APPLICABLE);
        assertThat(machine.evModel()).isNull();
    }

    @Test
    void a_road_tractor_has_no_equivalent_and_an_estimate_beyond_every_range_is_not_yet() {
        Assessment tractor = analysis.assess(vehicle("TRUCK", "CNG", "Iveco S-Way", 0, "Lyon", 1, null, null),
                usage(60, i -> 420));
        assertThat(tractor.status()).isEqualTo(Eligibility.NOT_APPLICABLE);
        assertThat(tractor.reason()).startsWith("Tracteur routier");

        VehicleFacts longHaulVan = new VehicleFacts(UUID.randomUUID(), UUID.randomUUID(), null, "AB-123-CD", "Renault",
                "Master", "VAN", "DIESEL", 2, 210, null, 330_000, LocalDate.of(2022, 3, 1), "OWNED", null, null);
        Assessment estimated = analysis.assess(longHaulVan, null);
        assertThat(estimated.status()).isEqualTo(Eligibility.NOT_YET);
        assertThat(estimated.dataSource()).isEqualTo("REGISTER");
        assertThat(estimated.reason()).contains("au-delà des 322 km");
    }

    @Test
    void the_annual_taxes_of_a_company_car_follow_the_co2_scale_and_spare_vans() {
        // 14 g at 0, 41 g at 1, 8 g at 2, 32 g at 3, 25 g at 4 euros, plus 40 euros of pollutants for a diesel
        assertThat(TransitionAnalysis.companyCarTax(vehicle("CAR", "DIESEL", "308", 120, null, 2, null, null)))
                .isEqualByComparingTo("293");
        assertThat(TransitionAnalysis.companyCarTax(vehicle("CAR", "PETROL", "308", 150, null, 1, null, null)))
                .isEqualByComparingTo(String.valueOf(41 + 16 + 96 + 200 + 50 + 20));
        assertThat(TransitionAnalysis.companyCarTax(vehicle("VAN", "DIESEL", "Trafic", 190, null, 2, null, null)))
                .isZero();
    }

    @Test
    void an_owned_vehicle_is_renewed_at_its_holding_age_or_in_the_coming_quarter_when_overdue() {
        VehicleFacts owned = vehicle("CAR", "DIESEL", "308", 120, null, 2, null, null);
        assertThat(analysis.renewal(owned)).isEqualTo(LocalDate.of(2027, 3, 1));
        VehicleFacts old = new VehicleFacts(UUID.randomUUID(), UUID.randomUUID(), null, "AB-123-CD", "Peugeot",
                "Partner", "VAN", "DIESEL", 3, 150, null, 190_000, LocalDate.of(2019, 1, 15), "OWNED", null, null);
        assertThat(analysis.renewal(old)).isEqualTo(TODAY.plusMonths(3));
    }

    @Test
    void the_segment_comes_from_the_model_of_the_vehicle() {
        assertThat(TransitionAnalysis.segment(vehicle("CAR", "DIESEL", "Skoda Superb", 0, null, 0, null, null)))
                .isEqualTo("LARGE");
        assertThat(TransitionAnalysis.segment(vehicle("CAR", "PLUG_IN_HYBRID", "BMW Série 3 Touring", 0, null, 0, null,
                null))).isEqualTo("LARGE");
        assertThat(TransitionAnalysis.segment(vehicle("CAR", "PETROL", "Clio V", 0, null, 0, null, null)))
                .isEqualTo("CITY");
        assertThat(TransitionAnalysis.segment(vehicle("VAN", "DIESEL", "Berlingo Van", 0, null, 0, null, null)))
                .isEqualTo("SMALL");
        assertThat(TransitionAnalysis.segment(vehicle("VAN", "DIESEL", "Trafic", 0, null, 0, null, null)))
                .isEqualTo("MEDIUM");
    }
}
