package com.fleetpilot.vehicle.domain.model;

/** How the client company holds the vehicle. */
public enum Acquisition {
    /** Location longue durée (long-term lease) with a lessor. */
    LEASE,
    /** Bought by the company. */
    OWNED
}
