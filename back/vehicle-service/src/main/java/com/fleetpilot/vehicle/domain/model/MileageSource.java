package com.fleetpilot.vehicle.domain.model;

/** Where an odometer reading comes from. */
public enum MileageSource {
    DRIVER,
    TELEMETRY,
    GARAGE,
    MANAGER,
    IMPORT,
    /** Reading at the return of a leased vehicle, entered by the lessor on its extranet. */
    LESSOR
}
