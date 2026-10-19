package com.fleetpilot.quote.infrastructure.demo;

import java.time.Clock;
import java.util.List;

import com.fleetpilot.quote.application.VehicleLookup;
import com.fleetpilot.quote.domain.analysis.Tariff;
import com.fleetpilot.quote.domain.model.VehicleRef;
import com.fleetpilot.quote.domain.repository.GarageRepository;
import com.fleetpilot.quote.domain.repository.QuoteRepository;
import com.fleetpilot.quote.domain.repository.VehicleRefRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the demo garages and quotes into an empty database at startup, when {@code fleetpilot.demo-data.enabled} is
 * true. The vehicles come from the fleet module: they are copied first, waiting for the fleet module if needed.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.demo-data.enabled", havingValue = "true")
public class QuoteDemoLoader {

    private static final Logger LOG = LoggerFactory.getLogger(QuoteDemoLoader.class);

    private final GarageRepository garages;
    private final QuoteRepository quotes;
    private final VehicleRefRepository vehicles;
    private final VehicleLookup vehicleLookup;
    private final Tariff tariff;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int syncAttempts;

    public QuoteDemoLoader(GarageRepository garages, QuoteRepository quotes, VehicleRefRepository vehicles,
            VehicleLookup vehicleLookup, Tariff tariff, JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock,
            @Value("${fleetpilot.demo-data.sync-attempts:30}") int syncAttempts) {
        this.garages = garages;
        this.quotes = quotes;
        this.vehicles = vehicles;
        this.vehicleLookup = vehicleLookup;
        this.tariff = tariff;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
        this.syncAttempts = syncAttempts;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() throws InterruptedException {
        long existing = garages.count();
        if (existing > 0) {
            LOG.info("Demo data not loaded: the database already contains {} garages", existing);
            return;
        }
        if (vehicles.count() == 0) {
            synchronizeVehicles();
        }
        long start = System.nanoTime();
        List<VehicleRef> fleet = vehicles.findAll();
        QuoteDemoGenerator.Result data = new QuoteDemoGenerator(QuoteDemoGenerator.DEFAULT_SEED, clock.instant(),
                clock.getZone(), tariff).generate(fleet);
        transactions.executeWithoutResult(status -> {
            garages.saveAll(data.garages());
            quotes.saveAll(data.quotes());
        });
        jdbc.queryForObject("select setval('quote_reference_seq', ?)", Long.class, data.quotes().size() + 1L);
        LOG.info("Demo data loaded: {} garages, {} quotes on {} vehicles in {} ms", data.garages().size(),
                data.quotes().size(), fleet.size(), (System.nanoTime() - start) / 1_000_000);
    }

    /** The fleet module may still be starting: retry for a while before giving up (quotes then stay empty). */
    private void synchronizeVehicles() throws InterruptedException {
        for (int attempt = 1; attempt <= syncAttempts; attempt++) {
            try {
                transactions.executeWithoutResult(status -> vehicleLookup.synchronizeAll());
                return;
            } catch (RuntimeException e) {
                LOG.info("Fleet module not reachable yet (attempt {}/{}): {}", attempt, syncAttempts, e.getMessage());
                Thread.sleep(5_000);
            }
        }
        LOG.warn("Vehicles could not be copied from the fleet module: demo quotes are not generated");
    }
}
