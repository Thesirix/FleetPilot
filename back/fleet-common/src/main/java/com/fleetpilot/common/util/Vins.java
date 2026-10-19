package com.fleetpilot.common.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Vehicle Identification Numbers (ISO 3779): 17 characters, digits and capital letters except I, O and Q.
 *
 * <p>The check digit in position 9 is only mandatory in North America, so it is not enforced for European
 * vehicles.
 */
public final class Vins {

    private static final Pattern VIN = Pattern.compile("^[A-HJ-NPR-Z0-9]{17}$");

    private Vins() {
    }

    /** Returns the canonical form (upper case, no spaces) of a VIN, or empty when it is not valid. */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = raw.toUpperCase(Locale.ROOT).replaceAll("\\s", "");
        return VIN.matcher(compact).matches() ? Optional.of(compact) : Optional.empty();
    }

    public static boolean isValid(String raw) {
        return normalize(raw).isPresent();
    }

    /** World Manufacturer Identifier: the first three characters (for example {@code VF1} for Renault). */
    public static String manufacturerCode(String vin) {
        return normalize(vin).map(v -> v.substring(0, 3))
                .orElseThrow(() -> new IllegalArgumentException("Invalid VIN: " + vin));
    }
}
