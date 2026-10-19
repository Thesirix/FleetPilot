package com.fleetpilot.vehicle.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** European driving licence categories relevant to company fleets. */
public enum LicenseCategory {
    B, BE, C1, C, CE, D;

    /** Categories that also allow driving this one (a C licence holder can drive C1 vehicles, CE covers C...). */
    public Set<LicenseCategory> coveredBy() {
        return switch (this) {
            case B -> EnumSet.of(B, BE, C1, C, CE, D);
            case BE -> EnumSet.of(BE, CE);
            case C1 -> EnumSet.of(C1, C, CE);
            case C -> EnumSet.of(C, CE);
            case CE -> EnumSet.of(CE);
            case D -> EnumSet.of(D);
        };
    }
}
