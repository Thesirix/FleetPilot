package com.fleetpilot.vehicle.domain.model;

/** Recurring obligations of a vehicle. */
public enum DeadlineType {
    /** Contrôle technique: mandatory roadworthiness test. */
    TECHNICAL_INSPECTION,
    /** Manufacturer service (révision). */
    SERVICE,
    /** Tyre replacement, by mileage. */
    TYRES,
    /** Calibration of the tachograph of trucks, every two years. */
    TACHOGRAPH,
    /** End of the long-term lease: the vehicle must be returned or the contract extended. */
    CONTRACT_END
}
