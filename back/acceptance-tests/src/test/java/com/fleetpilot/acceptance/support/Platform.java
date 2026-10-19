package com.fleetpilot.acceptance.support;

/**
 * Where the platform runs (docker compose up): the portal behind Nginx and the extranet of the lessors behind Apache.
 * Overridden with -Dportal.url=... and -Dextranet.url=... to test another environment.
 */
public final class Platform {

    public static final String PORTAL = System.getProperty("portal.url", "http://localhost");
    public static final String EXTRANET = System.getProperty("extranet.url", "http://localhost:8095/extranet");
    /** Password of every demo account. */
    public static final String PASSWORD = System.getProperty("demo.password", "FleetPilot2026!");

    private Platform() {
    }
}
