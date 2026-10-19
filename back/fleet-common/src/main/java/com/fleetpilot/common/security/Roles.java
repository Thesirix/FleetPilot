package com.fleetpilot.common.security;

/**
 * Application roles, as defined in the Keycloak realm {@code fleetpilot}.
 *
 * <p>FleetPilot staff (fleet managers working for the client companies) see every client company; client users only
 * see the data of their own company. The {@code *_ONLY} and {@code ANY_*} constants are Spring Security expressions
 * for {@code @PreAuthorize}.
 */
public final class Roles {

    /** Administrator of the platform: settings, client companies, users. Comes from the staff directory (LDAP). */
    public static final String PLATFORM_ADMIN = "platform-admin";
    /** Fleet manager of the fleet management company: manages the fleets of every client. Comes from LDAP. */
    public static final String FLEET_MANAGER = "fleet-manager";
    /** Fleet manager on the client side: manages the fleet of their own company. */
    public static final String CLIENT_MANAGER = "client-manager";
    /** Driver: sees their vehicle, deadlines, declares incidents (Android app and PWA). */
    public static final String DRIVER = "driver";
    /** Approved garage: submits quotes and invoices. */
    public static final String GARAGE = "garage";
    /** Machine-to-machine supplier integration (lessors, insurers, fuel cards). */
    public static final String API_SUPPLIER = "api-supplier";

    /** Platform administrators only. */
    public static final String ADMIN_ONLY = "hasRole('" + PLATFORM_ADMIN + "')";
    /** Staff of the fleet management company (every client company). */
    public static final String ANY_STAFF = "hasAnyRole('" + PLATFORM_ADMIN + "', '" + FLEET_MANAGER + "')";
    /** Anyone who manages a fleet: staff, or a manager of the client company (restricted to their company). */
    public static final String ANY_MANAGER = "hasAnyRole('" + PLATFORM_ADMIN + "', '" + FLEET_MANAGER + "', '"
            + CLIENT_MANAGER + "')";
    /** Fleet managers and supplier integrations. */
    public static final String ANY_MANAGER_OR_SUPPLIER = "hasAnyRole('" + PLATFORM_ADMIN + "', '" + FLEET_MANAGER + "', '"
            + CLIENT_MANAGER + "', '" + API_SUPPLIER + "')";
    /** Managers, and drivers for what concerns them (photos of their own claims and inspections). */
    public static final String ANY_MANAGER_OR_DRIVER = "hasAnyRole('" + PLATFORM_ADMIN + "', '" + FLEET_MANAGER + "', '"
            + CLIENT_MANAGER + "', '" + DRIVER + "')";
    /** Drivers, for their own space. */
    public static final String DRIVER_ONLY = "hasRole('" + DRIVER + "')";
    /** Approved garages, for their own space. */
    public static final String GARAGE_ONLY = "hasRole('" + GARAGE + "')";
    /** Everyone involved in garage quotes: managers (each restricted to their scope) and garages (their own quotes). */
    public static final String ANY_MANAGER_OR_GARAGE = "hasAnyRole('" + PLATFORM_ADMIN + "', '" + FLEET_MANAGER + "', '"
            + CLIENT_MANAGER + "', '" + GARAGE + "')";
    /** Garage actions on a quote: the garage itself, staff acting on its behalf, or the integration bus. */
    public static final String GARAGE_OR_STAFF = "hasAnyRole('" + PLATFORM_ADMIN + "', '" + FLEET_MANAGER + "', '"
            + GARAGE + "', '" + API_SUPPLIER + "')";

    private Roles() {
    }
}
