package com.fleetpilot.quote.unit;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import com.fleetpilot.quote.domain.analysis.Tariff;
import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.TariffClass;

/** A small extract of the reference tariff (same values as the Flyway migration), for the unit tests. */
public final class TestTariff implements Tariff {

    private static final Map<String, BigDecimal[]> PARTS = Map.of(
            "PLAQ-AV", prices("45.00", "58.00", "165.00"),
            "DISQ-AV", prices("95.00", "130.00", "380.00"),
            "FILT-HUILE", prices("12.00", "15.00", "45.00"),
            "HUILE-5W30", prices("11.00", "11.00", "9.00"),
            "PNEU", prices("95.00", "120.00", "420.00"));

    private static final Map<String, Object[]> OPERATIONS = Map.of(
            "FREIN-AV-DISQ", new Object[] {LabourCategory.MECH, prices("1.30", "1.50", "2.80")},
            "REVISION", new Object[] {LabourCategory.MECH, prices("1.00", "1.20", "2.50")},
            "DIAG", new Object[] {LabourCategory.ELEC, prices("0.50", "0.50", "1.00")},
            "PEINTURE", new Object[] {LabourCategory.PAINT, prices("2.50", "2.80", "4.00")});

    @Override
    public Optional<PartPrice> part(String code, TariffClass tariffClass) {
        return Optional.ofNullable(PARTS.get(code)).map(p -> new PartPrice(code, code, p[tariffClass.ordinal()]));
    }

    @Override
    public Optional<StandardTime> operation(String code, TariffClass tariffClass) {
        return Optional.ofNullable(OPERATIONS.get(code)).map(o -> new StandardTime(code, code, (LabourCategory) o[0],
                ((BigDecimal[]) o[1])[tariffClass.ordinal()]));
    }

    /** Provence: 72 EUR mechanics, 78 EUR electrics, 74 EUR paint; trucks 92 / 98 / 93. No rate in Grand Est. */
    @Override
    public Optional<BigDecimal> hourlyRate(Region region, LabourCategory category, TariffClass tariffClass) {
        if (region != Region.PAC) {
            return Optional.empty();
        }
        boolean truck = tariffClass == TariffClass.TRUCK;
        String rate = switch (category) {
            case MECH -> truck ? "92.00" : "72.00";
            case ELEC -> truck ? "98.00" : "78.00";
            case BODY -> truck ? "88.00" : "70.00";
            case PAINT -> truck ? "93.00" : "74.00";
        };
        return Optional.of(new BigDecimal(rate));
    }

    private static BigDecimal[] prices(String car, String van, String truck) {
        return new BigDecimal[] {new BigDecimal(car), new BigDecimal(van), new BigDecimal(truck)};
    }
}
