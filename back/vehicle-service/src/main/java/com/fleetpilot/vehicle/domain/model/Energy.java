package com.fleetpilot.vehicle.domain.model;

/** Energy (fuel type) of a vehicle. */
public enum Energy {
    PETROL,
    DIESEL,
    HYBRID,
    PLUG_IN_HYBRID,
    ELECTRIC,
    LPG,
    CNG,
    HYDROGEN;

    /** Zero tailpipe emission vehicles get the green "Crit'Air E" sticker. */
    public boolean isZeroEmission() {
        return this == ELECTRIC || this == HYDROGEN;
    }
}
