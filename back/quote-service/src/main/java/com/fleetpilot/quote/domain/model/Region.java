package com.fleetpilot.quote.domain.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/** French regions covered by the garage network, each with its own negotiated hourly rates. */
public enum Region {

    IDF("Île-de-France", Set.of("75", "77", "78", "91", "92", "93", "94", "95")),
    PAC("Provence-Alpes-Côte d'Azur", Set.of("04", "05", "06", "13", "83", "84")),
    ARA("Auvergne-Rhône-Alpes", Set.of("01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74")),
    HDF("Hauts-de-France", Set.of("02", "59", "60", "62", "80")),
    NAQ("Nouvelle-Aquitaine", Set.of("16", "17", "19", "23", "24", "33", "40", "47", "64", "79", "86", "87")),
    OCC("Occitanie", Set.of("09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82")),
    PDL("Pays de la Loire", Set.of("44", "49", "53", "72", "85")),
    GES("Grand Est", Set.of("08", "10", "51", "52", "54", "55", "57", "67", "68", "88"));

    private final String label;
    private final Set<String> departments;

    Region(String label, Set<String> departments) {
        this.label = label;
        this.departments = departments;
    }

    public String label() {
        return label;
    }

    /** Region of a French postal code, from its department (the first two digits). */
    public static Optional<Region> ofPostalCode(String postalCode) {
        if (postalCode == null || postalCode.length() < 2) {
            return Optional.empty();
        }
        String department = postalCode.substring(0, 2);
        return Arrays.stream(values()).filter(region -> region.departments.contains(department)).findFirst();
    }
}
