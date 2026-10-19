package com.fleetpilot.quote.application;

import java.util.Comparator;

import com.fleetpilot.common.error.ResourceNotFoundException;
import com.fleetpilot.quote.application.command.QuoteCommands.UpdateRate;
import com.fleetpilot.quote.application.view.QuoteViews.OperationView;
import com.fleetpilot.quote.application.view.QuoteViews.PartView;
import com.fleetpilot.quote.application.view.QuoteViews.RateView;
import com.fleetpilot.quote.application.view.QuoteViews.TariffView;
import com.fleetpilot.quote.domain.model.LabourCategory;
import com.fleetpilot.quote.domain.model.LabourRate;
import com.fleetpilot.quote.domain.model.Region;
import com.fleetpilot.quote.domain.repository.LabourOperationRepository;
import com.fleetpilot.quote.domain.repository.LabourRateRepository;
import com.fleetpilot.quote.domain.repository.PartReferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The reference tariff: consulted by everyone involved in quotes, updated by administrators. */
@Service
@Transactional
public class TariffService {

    /** Called after a change, so that the next analyses use the new values. */
    public interface TariffChangeListener {
        void tariffChanged();
    }

    private final PartReferenceRepository parts;
    private final LabourOperationRepository operations;
    private final LabourRateRepository rates;
    private final TariffChangeListener listener;

    public TariffService(PartReferenceRepository parts, LabourOperationRepository operations, LabourRateRepository rates,
            TariffChangeListener listener) {
        this.parts = parts;
        this.operations = operations;
        this.rates = rates;
        this.listener = listener;
    }

    @Transactional(readOnly = true)
    public TariffView tariff() {
        return new TariffView(
                parts.findAllByOrderByFamilyAscCodeAsc().stream().map(PartView::of).toList(),
                operations.findAllByOrderByLabourCategoryAscCodeAsc().stream().map(OperationView::of).toList(),
                rates.findAll().stream().map(RateView::of)
                        .sorted(Comparator.comparing(RateView::region).thenComparing(RateView::labourCategory))
                        .toList());
    }

    public RateView updateRate(Region region, LabourCategory category, UpdateRate cmd) {
        LabourRate rate = rates.findById(new LabourRate.Key(region, category))
                .orElseThrow(() -> new ResourceNotFoundException("Labour rate", region + "/" + category));
        rate.update(cmd.hourlyRate(), cmd.truckHourlyRate());
        listener.tariffChanged();
        return RateView.of(rate);
    }
}
