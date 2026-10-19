package com.fleetpilot.integration.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fleetpilot.integration.demo.PartnerFiles.DemoFile;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference.Garage;
import com.fleetpilot.integration.fleet.FleetDirectory;
import com.fleetpilot.integration.fleet.FleetDirectory.FleetVehicle;
import com.fleetpilot.integration.journal.ExchangeJournal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * At the first start with demo data, drops the files the partners would have sent into the inbox, once the fleet and
 * quote modules answer. The routes then handle them exactly as real files: the journal, the quotes, the fuel
 * transactions of the demonstration all come from the real path.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.demo-data.enabled", havingValue = "true")
public class PartnerFilesLoader {

    private static final Logger LOG = LoggerFactory.getLogger(PartnerFilesLoader.class);
    private static final List<String> GARAGES = List.of("MRS-01", "MRS-02", "LYS-02", "LYS-06", "PAR-01");

    private final FleetDirectory fleet;
    private final QuoteReference quotes;
    private final ExchangeJournal journal;
    private final Path inbox;
    private final Clock clock;

    public PartnerFilesLoader(FleetDirectory fleet, QuoteReference quotes, ExchangeJournal journal,
            @Value("${fleetpilot.integration.inbox}") String inbox, Clock clock) {
        this.fleet = fleet;
        this.quotes = quotes;
        this.journal = journal;
        this.inbox = Path.of(inbox);
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dropOnFirstStart() {
        if (!journal.isEmpty()) {
            return;
        }
        Thread.ofVirtual().name("partner-files").start(() -> {
            for (int attempt = 1; attempt <= 40; attempt++) {
                try {
                    List<DemoFile> files = build();
                    files.forEach(this::write);
                    LOG.info("Partner files of the demonstration dropped: {} files", files.size());
                    return;
                } catch (RuntimeException e) {
                    LOG.info("Business services not reachable yet (attempt {}): {}", attempt, e.getMessage());
                    sleep();
                }
            }
            LOG.warn("Partner files not dropped: the business services never answered");
        });
    }

    List<DemoFile> build() {
        PartnerFiles partners = new PartnerFiles(LocalDate.now(clock));
        List<FleetVehicle> vehicles = fleet.vehicles();
        Map<String, Garage> garages = new LinkedHashMap<>();
        GARAGES.forEach(code -> quotes.garage(code).ifPresent(g -> garages.put(code, g)));
        List<DemoFile> files = new ArrayList<>();
        files.addAll(partners.fuelCards("Carbu Pro", "CBP", vehicles, false));
        files.addAll(partners.fuelCards("Route Poids Lourds", "RPL", vehicles, true));
        files.add(partners.lessorInvoice("Ayvens", "AYV", fleet.contracts("Ayvens"), false));
        files.add(partners.lessorInvoice("Arval", "ARV", fleet.contracts("Arval"), true));
        if (garages.size() == GARAGES.size()) {
            files.addAll(partners.garageQuotes(vehicles, garages, quotes.tariff()));
        }
        return files;
    }

    private void write(DemoFile file) {
        try {
            Path folder = inbox.resolve(file.folder());
            Files.createDirectories(folder);
            Path temporary = folder.resolve("." + file.name() + ".part");
            Files.writeString(temporary, file.content(), StandardCharsets.UTF_8);
            Files.move(temporary, folder.resolve(file.name()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(6_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
