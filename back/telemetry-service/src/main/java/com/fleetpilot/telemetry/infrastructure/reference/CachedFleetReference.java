package com.fleetpilot.telemetry.infrastructure.reference;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.fleetpilot.telemetry.application.FleetReference;
import com.fleetpilot.telemetry.domain.LiveVehicle.VehicleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Category, energy and next service mileage of every vehicle, copied from the fleet module every hour into the
 * {@code vehicle_reference} table and kept in memory: the rules read it for every position, without any network call.
 * If the fleet module is unreachable, the last copy stays in use.
 */
public class CachedFleetReference implements FleetReference {

    private static final Logger LOG = LoggerFactory.getLogger(CachedFleetReference.class);
    private static final VehicleInfo UNKNOWN = new VehicleInfo("CAR", null, null);

    private final FleetModule fleet;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final Map<UUID, VehicleInfo> cache = new ConcurrentHashMap<>();

    public CachedFleetReference(FleetModule fleet, JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.fleet = fleet;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
        loadLocalCopy();
    }

    @Override
    public VehicleInfo info(UUID vehicleId) {
        return cache.getOrDefault(vehicleId, UNKNOWN);
    }

    /** Every hour, and a minute after the start (the fleet module may still be starting). */
    @Scheduled(initialDelay = 20_000, fixedDelay = 3_600_000)
    public void refresh() {
        try {
            List<FleetModule.Vehicle> vehicles = fleet.vehicles();
            Map<UUID, Integer> due = new HashMap<>();
            fleet.servicesDue().forEach(s -> due.merge(s.vehicleId(), s.dueMileage(), Math::min));
            Timestamp now = Timestamp.from(clock.instant());
            transactions.executeWithoutResult(status -> jdbc.batchUpdate("""
                    insert into vehicle_reference (vehicle_id, category, energy, service_due_mileage, refreshed_at)
                    values (?, ?, ?, ?, ?)
                    on conflict (vehicle_id) do update set category = excluded.category, energy = excluded.energy,
                        service_due_mileage = excluded.service_due_mileage, refreshed_at = excluded.refreshed_at""",
                    vehicles.stream().map(v -> new Object[] {v.id(), v.category(), v.energy(), due.get(v.id()), now})
                            .toList()));
            vehicles.forEach(v -> cache.put(v.id(), new VehicleInfo(v.category(), v.energy(), due.get(v.id()))));
            LOG.info("Fleet reference refreshed: {} vehicles, {} services due by mileage", vehicles.size(), due.size());
        } catch (RuntimeException e) {
            LOG.warn("Fleet reference not refreshed, the last copy stays in use: {}", e.getMessage());
        }
    }

    private void loadLocalCopy() {
        jdbc.query("select vehicle_id, category, energy, service_due_mileage from vehicle_reference", rs -> {
            cache.put(rs.getObject("vehicle_id", UUID.class), new VehicleInfo(rs.getString("category"),
                    rs.getString("energy"), (Integer) rs.getObject("service_due_mileage")));
        });
    }
}
