package com.fleetpilot.quote.domain.model;

import java.util.Locale;

/**
 * Vehicle class used by the reference tariff. Fleet categories are mapped to it: site machinery is priced like a
 * truck, and an unknown vehicle like a car.
 */
public enum TariffClass {
    CAR,
    VAN,
    TRUCK;

    /** Maps a vehicle category of the fleet module (CAR, VAN, TRUCK, MACHINE). */
    public static TariffClass ofVehicleCategory(String category) {
        if (category == null) {
            return CAR;
        }
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "VAN" -> VAN;
            case "TRUCK", "MACHINE" -> TRUCK;
            default -> CAR;
        };
    }
}
