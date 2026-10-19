package com.fleetpilot.common.telemetry;

import java.util.UUID;

/**
 * Which vehicles of the demonstration fleet carry a connected box. In a real fleet some vehicles are not equipped
 * (short leases, vehicles about to leave, a client that did not subscribe): here four vehicles in five are, chosen
 * from the vehicle id so that the simulator, the usage history and the analyses agree.
 */
public final class ConnectedFleet {

    /** Share of the fleet equipped with a box, in percent. */
    public static final int EQUIPPED_PERCENT = 80;

    private ConnectedFleet() {
    }

    public static boolean isEquipped(UUID vehicleId) {
        return Math.floorMod(vehicleId.getLeastSignificantBits(), 100) < EQUIPPED_PERCENT;
    }
}
