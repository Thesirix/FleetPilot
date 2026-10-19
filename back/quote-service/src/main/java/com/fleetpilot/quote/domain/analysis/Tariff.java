package com.fleetpilot.quote.domain.analysis;

import java.math.BigDecimal;
import java.util.Optional;

import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.TariffClass;

/** The reference tariff, as seen by the analysis: prices, standard times and negotiated rates. */
public interface Tariff {

    /** Reference price of a part. */
    record PartPrice(String code, String label, BigDecimal price) {
    }

    /** Standard time of a labour operation. */
    record StandardTime(String code, String label, LabourCategory category, BigDecimal hours) {
    }

    Optional<PartPrice> part(String code, TariffClass tariffClass);

    Optional<StandardTime> operation(String code, TariffClass tariffClass);

    /** Negotiated hourly rate of the region for this labour category. */
    Optional<BigDecimal> hourlyRate(Region region, LabourCategory category, TariffClass tariffClass);
}
