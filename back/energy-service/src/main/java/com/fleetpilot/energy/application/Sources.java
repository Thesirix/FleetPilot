package com.fleetpilot.energy.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fleetpilot.energy.domain.Transition.Usage;
import com.fleetpilot.energy.domain.Transition.VehicleFacts;

/** Where the analysis reads the fleet and its usage. */
public final class Sources {

    private Sources() {
    }

    /** The vehicles of the fleet (in service), with their lease when they have one. */
    public interface FleetData {
        List<VehicleFacts> vehicles();
    }

    /** The distance of every day driven over the last days, per vehicle. */
    public interface UsageData {
        Map<UUID, Usage> usage(int days);
    }
}
