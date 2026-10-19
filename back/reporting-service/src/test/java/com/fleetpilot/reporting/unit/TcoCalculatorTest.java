package com.fleetpilot.reporting.unit;

import static com.fleetpilot.reporting.unit.FakeSources.CLIO;
import static com.fleetpilot.reporting.unit.FakeSources.NOW;
import static com.fleetpilot.reporting.unit.FakeSources.TRAFIC;
import static com.fleetpilot.reporting.unit.FakeSources.ZOE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.List;

import com.fleetpilot.reporting.application.TcoCalculator;
import com.fleetpilot.reporting.application.TcoCalculator.Indicators;
import com.fleetpilot.reporting.application.TcoCalculator.VehicleCost;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Total cost of ownership over twelve months")
class TcoCalculatorTest {

    private final FakeSources sources = new FakeSources();
    private final TcoCalculator calculator = new TcoCalculator(NOW, ZoneId.of("Europe/Paris"));

    private List<VehicleCost> costs() {
        return calculator.costs(sources.vehicles(null), sources.activeContracts(null), sources.quotes(null, null, null),
                sources.incidents(null));
    }

    private VehicleCost of(List<VehicleCost> costs, String registration) {
        return costs.stream().filter(c -> c.vehicle().registration().equals(registration)).findFirst().orElseThrow();
    }

    @Test
    void rent_maintenance_and_claims_of_the_year_make_the_cost_of_a_vehicle() {
        VehicleCost clio = of(costs(), CLIO.registration());
        assertThat(clio.rent()).isEqualByComparingTo("4800.00");
        assertThat(clio.maintenance()).isEqualByComparingTo("300.00");
        assertThat(clio.claims()).as("the claim of two years ago does not count").isEqualByComparingTo("1200.00");
        assertThat(clio.total()).isEqualByComparingTo("6300.00");
        assertThat(clio.kilometres()).isEqualTo(14_600);
        assertThat(clio.costPerKm()).isEqualByComparingTo("0.432");
    }

    @Test
    void an_owned_vehicle_has_no_rent_and_only_decided_quotes_count() {
        VehicleCost van = of(costs(), TRAFIC.registration());
        assertThat(van.lessor()).isNull();
        assertThat(van.rent()).isEqualByComparingTo("0");
        assertThat(van.maintenance()).as("the rejected quote does not count").isEqualByComparingTo("500.00");
        assertThat(van.costPerKm()).isNull();

        VehicleCost zoe = of(costs(), ZOE.registration());
        assertThat(zoe.maintenance()).as("an open quote does not count").isEqualByComparingTo("0");
    }

    @Test
    void disposed_vehicles_are_left_out_and_the_most_expensive_come_first() {
        List<VehicleCost> costs = costs();
        assertThat(costs).hasSize(3);
        assertThat(costs.getFirst().vehicle()).isEqualTo(CLIO);
    }

    @Test
    void the_fleet_indicators_add_up() {
        List<VehicleCost> costs = costs();
        Indicators indicators = calculator.indicators(sources.vehicles(null), costs, sources.incidents(null));
        assertThat(indicators.vehicles()).isEqualTo(3);
        // 6,300 + 4,200 + 500
        assertThat(indicators.totalCost()).isEqualByComparingTo("11000.00");
        assertThat(indicators.monthlyCostPerVehicle()).isEqualByComparingTo("305.56");
        assertThat(indicators.costPerKm()).as("only vehicles with known kilometres").isEqualByComparingTo("0.432");
        assertThat(indicators.claimsPer100Vehicles()).isEqualByComparingTo("33.3");
        assertThat(indicators.immobilisationRate()).isEqualByComparingTo("33.3");
        assertThat(indicators.leasedShare()).isEqualByComparingTo("66.7");
    }

    @Test
    void an_empty_fleet_gives_zeros_not_errors() {
        Indicators indicators = calculator.indicators(List.of(), List.of(), List.of());
        assertThat(indicators.vehicles()).isZero();
        assertThat(indicators.monthlyCostPerVehicle()).isEqualByComparingTo("0");
        assertThat(indicators.costPerKm()).isNull();
    }
}
