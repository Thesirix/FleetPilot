package com.fleetpilot.simulator.engine;

import java.util.UUID;

/**
 * A vehicle of the fleet as the simulator sees it, and the behaviour it plays.
 *
 * @param site      the site it starts from and comes back to
 * @param mileage   odometer at the start of the simulation
 * @param behaviour a driving style used to trigger the alert rules of the telemetry service
 */
public record VehicleProfile(UUID id, UUID companyId, String registration, String category, String energy,
        String site, int mileage, Behaviour behaviour) {

    /** Driving styles of the simulated drivers. */
    public enum Behaviour {
        /** Drives at the speed of the traffic. */
        CALM,
        /** Exceeds the limit on fast roads. */
        SPEEDER,
        /** Waits with the engine running at the customers. */
        IDLER,
        /** Has a recurring engine fault. */
        FAULTY
    }

    public boolean electric() {
        return "ELECTRIC".equals(energy) || "HYDROGEN".equals(energy);
    }

    /** Range of a full battery in kilometres, by category (electric vehicles only). */
    public double rangeKm() {
        return switch (category) {
            case "VAN" -> 230;
            case "TRUCK", "MACHINE" -> 280;
            default -> 380;
        };
    }
}
