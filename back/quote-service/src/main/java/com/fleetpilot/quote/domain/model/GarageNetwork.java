package com.fleetpilot.quote.domain.model;

/** Kind of workshop. */
public enum GarageNetwork {
    /** Brand dealer (concession): manufacturer's network. */
    DEALER,
    /** Independent multi-brand garage. */
    INDEPENDENT,
    /** Fast-fit centre: tyres, brakes, batteries, servicing. */
    FAST_FIT,
    /** Body shop (carrosserie). */
    BODYWORK,
    /** Truck workshop (poids lourds). */
    TRUCK
}
