package com.fleetpilot.vehicle.domain.model;

import java.util.Optional;

/** Vehicle categories used by French fleet managers. */
public enum VehicleCategory {

    /** Véhicule léger: passenger car. */
    CAR("VL", LicenseCategory.B),
    /** Véhicule utilitaire: light commercial vehicle up to 3.5 t. */
    VAN("VU", LicenseCategory.B),
    /** Poids lourd: truck over 3.5 t. */
    TRUCK("PL", LicenseCategory.C),
    /** Engin: site machinery, driven with a CACES certificate instead of a licence. */
    MACHINE("Engin", null);

    private final String frenchCode;
    private final LicenseCategory requiredLicense;

    VehicleCategory(String frenchCode, LicenseCategory requiredLicense) {
        this.frenchCode = frenchCode;
        this.requiredLicense = requiredLicense;
    }

    public String frenchCode() {
        return frenchCode;
    }

    /** Driving licence category needed to drive this kind of vehicle, empty when no licence applies. */
    public Optional<LicenseCategory> requiredLicense() {
        return Optional.ofNullable(requiredLicense);
    }
}
