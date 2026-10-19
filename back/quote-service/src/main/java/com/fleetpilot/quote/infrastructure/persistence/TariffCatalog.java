package com.fleetpilot.quote.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.quote.application.TariffService.TariffChangeListener;
import com.fleetpilot.quote.domain.analysis.Tariff;
import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.LabourOperation;
import com.fleetpilot.quote.domain.model.LabourRate;
import com.fleetpilot.quote.domain.model.PartReference;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.model.TariffClass;
import com.fleetpilot.quote.domain.repository.LabourOperationRepository;
import com.fleetpilot.quote.domain.repository.LabourRateRepository;
import com.fleetpilot.quote.domain.repository.PartReferenceRepository;
import org.springframework.stereotype.Component;

/**
 * The reference tariff in memory: a few hundred rows, read once and kept until an administrator changes a value.
 * Analysing a quote then costs no query at all.
 */
@Component
public class TariffCatalog implements Tariff, TariffChangeListener {

    private record Snapshot(Map<String, PartReference> parts, Map<String, LabourOperation> operations,
            Map<LabourRate.Key, LabourRate> rates) {
    }

    private final PartReferenceRepository parts;
    private final LabourOperationRepository operations;
    private final LabourRateRepository rates;
    private volatile Snapshot snapshot;

    public TariffCatalog(PartReferenceRepository parts, LabourOperationRepository operations, LabourRateRepository rates) {
        this.parts = parts;
        this.operations = operations;
        this.rates = rates;
    }

    @Override
    public Optional<PartPrice> part(String code, TariffClass tariffClass) {
        return Optional.ofNullable(current().parts().get(code))
                .map(p -> new PartPrice(p.getCode(), p.getLabel(), p.priceFor(tariffClass)));
    }

    @Override
    public Optional<StandardTime> operation(String code, TariffClass tariffClass) {
        return Optional.ofNullable(current().operations().get(code))
                .map(o -> new StandardTime(o.getCode(), o.getLabel(), o.getLabourCategory(), o.hoursFor(tariffClass)));
    }

    @Override
    public Optional<BigDecimal> hourlyRate(Region region, LabourCategory category, TariffClass tariffClass) {
        return Optional.ofNullable(current().rates().get(new LabourRate.Key(region, category)))
                .map(rate -> rate.rateFor(tariffClass));
    }

    /** The next analysis reloads the tariff, after the transaction of the change is committed. */
    @Override
    public void tariffChanged() {
        snapshot = null;
    }

    private Snapshot current() {
        Snapshot loaded = snapshot;
        if (loaded == null) {
            loaded = new Snapshot(
                    parts.findAll().stream().collect(Collectors.toUnmodifiableMap(PartReference::getCode, Function.identity())),
                    operations.findAll().stream()
                            .collect(Collectors.toUnmodifiableMap(LabourOperation::getCode, Function.identity())),
                    rates.findAll().stream().collect(Collectors.toUnmodifiableMap(LabourRate::getKey, Function.identity())));
            snapshot = loaded;
        }
        return loaded;
    }
}
