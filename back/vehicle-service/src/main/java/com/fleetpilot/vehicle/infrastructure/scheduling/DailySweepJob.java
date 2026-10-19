package com.fleetpilot.vehicle.infrastructure.scheduling;

import com.fleetpilot.vehicle.application.FleetSweepService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the fleet sweep every morning, before fleet managers start their day. */
@Component
@ConditionalOnProperty(name = "fleetpilot.sweep.enabled", havingValue = "true", matchIfMissing = true)
public class DailySweepJob {

    private final FleetSweepService sweep;

    public DailySweepJob(FleetSweepService sweep) {
        this.sweep = sweep;
    }

    @Scheduled(cron = "${fleetpilot.sweep.cron:0 0 6 * * *}", zone = "Europe/Paris")
    public void run() {
        sweep.run();
    }
}
