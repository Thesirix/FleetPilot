package com.fleetpilot.simulator.fleet;

import java.util.List;

import com.fleetpilot.simulator.engine.FleetSelection;
import com.fleetpilot.simulator.engine.Simulation;
import com.fleetpilot.simulator.engine.VehicleProfile;
import com.fleetpilot.simulator.road.RoadNetwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Starts the simulation once the fleet is known (the fleet module may still be starting: it is asked again until it
 * answers), then advances it at a fixed rate.
 */
public class SimulatorRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatorRunner.class);

    private final FleetClient fleet;
    private final RoadNetwork roads;
    private final Simulation simulation;
    private final int vehicleCount;

    public SimulatorRunner(FleetClient fleet, RoadNetwork roads, Simulation simulation,
            @Value("${fleetpilot.simulator.vehicles:160}") int vehicleCount) {
        this.fleet = fleet;
        this.roads = roads;
        this.simulation = simulation;
        this.vehicleCount = vehicleCount;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void startWhenTheFleetIsKnown() throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                List<VehicleProfile> profiles = FleetSelection.select(fleet.vehicles(), roads.sites(), vehicleCount);
                simulation.start(profiles);
                LOG.info("Simulation started: {} connected vehicles on {} routes", profiles.size(), roads.routeCount());
                return;
            } catch (RuntimeException e) {
                LOG.info("Fleet module not reachable yet (attempt {}): {}", attempt, e.getMessage());
                Thread.sleep(Math.min(30_000, 2_000L * attempt));
            }
        }
    }

    @Scheduled(fixedRateString = "${fleetpilot.simulator.tick:2000}")
    public void tick() {
        simulation.tick();
    }
}
