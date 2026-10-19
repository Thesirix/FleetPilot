package com.fleetpilot.telemetry.infrastructure.demo;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.fleetpilot.telemetry.infrastructure.reference.FleetModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Loads ninety days of usage history at the first start ({@code fleetpilot.demo-data.enabled}), once the fleet module
 * answers. The live simulation then adds the current days on top.
 */
@Component
@ConditionalOnProperty(name = "fleetpilot.demo-data.enabled", havingValue = "true")
public class UsageHistoryLoader {

    static final long SEED = 20_261_016L;
    private static final Logger LOG = LoggerFactory.getLogger(UsageHistoryLoader.class);

    private final FleetModule fleet;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public UsageHistoryLoader(FleetModule fleet, JdbcTemplate jdbc, Clock clock) {
        this.fleet = fleet;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void loadIfEmpty() throws InterruptedException {
        Long existing = jdbc.queryForObject("select count(*) from daily_usage", Long.class);
        if (existing != null && existing > 0) {
            LOG.info("Usage history not loaded: {} days already recorded", existing);
            return;
        }
        for (int attempt = 1; attempt <= 40; attempt++) {
            try {
                load();
                return;
            } catch (RuntimeException e) {
                LOG.info("Fleet module not reachable yet (attempt {}): {}", attempt, e.getMessage());
                Thread.sleep(5_000);
            }
        }
        LOG.warn("Usage history not loaded: the fleet module never answered");
    }

    void load() {
        long start = System.nanoTime();
        LocalDate today = LocalDate.now(clock);
        List<UsageHistoryGenerator.Day> days = new UsageHistoryGenerator(SEED, today).generate(fleet.vehicles());
        jdbc.batchUpdate("""
                insert into daily_usage (vehicle_id, company_id, day, distance_km, trips, longest_trip_km, driving_minutes,
                    max_speed) values (?, ?, ?, ?, ?, ?, ?, ?) on conflict do nothing""",
                days.stream().map(d -> new Object[] {d.vehicleId(), d.companyId(), Date.valueOf(d.day()), d.distanceKm(),
                    d.trips(), d.longestTripKm(), d.drivingMinutes(), d.maxSpeed()}).toList());
        LOG.info("Usage history loaded: {} vehicle days in {} ms", days.size(), (System.nanoTime() - start) / 1_000_000);
    }
}
