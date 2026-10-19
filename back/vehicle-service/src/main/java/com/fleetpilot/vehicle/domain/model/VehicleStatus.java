package com.fleetpilot.vehicle.domain.model;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a vehicle in the fleet. */
public enum VehicleStatus {
    /** Ordered from the lessor, not delivered yet. */
    AWAITING_DELIVERY,
    /** In service. */
    ACTIVE,
    /** At the garage (service, repair after an incident). */
    IN_REPAIR,
    /** Cannot be driven (waiting for parts, expertise, administrative block). */
    IMMOBILIZED,
    /** Returned to the lessor or sold: kept for history, can no longer change. */
    DISPOSED;

    public Set<VehicleStatus> allowedTransitions() {
        return switch (this) {
            case AWAITING_DELIVERY -> EnumSet.of(ACTIVE, DISPOSED);
            case ACTIVE -> EnumSet.of(IN_REPAIR, IMMOBILIZED, DISPOSED);
            case IN_REPAIR -> EnumSet.of(ACTIVE, IMMOBILIZED, DISPOSED);
            case IMMOBILIZED -> EnumSet.of(ACTIVE, IN_REPAIR, DISPOSED);
            case DISPOSED -> EnumSet.noneOf(VehicleStatus.class);
        };
    }

    public boolean canTransitionTo(VehicleStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Statuses in which a vehicle is part of the fleet the client pays for. */
    public boolean isInFleet() {
        return this != DISPOSED;
    }
}
