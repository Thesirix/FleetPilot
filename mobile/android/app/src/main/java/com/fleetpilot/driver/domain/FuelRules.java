package com.fleetpilot.driver.domain;

import java.util.List;

/**
 * What the app checks on a receipt before sending it, with the same bounds as the server: the driver hears about a
 * typo at once, even offline, instead of after the network comes back.
 */
public final class FuelRules {

    public static final String FUEL = "FUEL";
    public static final String CHARGE = "CHARGE";

    private FuelRules() {
    }

    /** An electric vehicle only charges, a plug-in hybrid does both, every other vehicle only fills up. */
    public static List<String> kindsFor(String energy) {
        if ("ELECTRIC".equals(energy)) {
            return List.of(CHARGE);
        }
        if ("PLUG_IN_HYBRID".equals(energy)) {
            return List.of(FUEL, CHARGE);
        }
        return List.of(FUEL);
    }

    /** Price per litre or kWh, or null while the quantity or the amount is missing. */
    public static Double unitPrice(Double quantity, Double amount) {
        if (quantity == null || amount == null || quantity <= 0 || amount < 0) {
            return null;
        }
        return amount / quantity;
    }

    /** Charging at the depot can be free, but not above 1.50 € a kWh; fuel costs between 0.50 € and 4 € a litre. */
    public static boolean plausible(String kind, double unitPrice) {
        return CHARGE.equals(kind) ? unitPrice <= 1.50 : unitPrice >= 0.50 && unitPrice <= 4.00;
    }
}
