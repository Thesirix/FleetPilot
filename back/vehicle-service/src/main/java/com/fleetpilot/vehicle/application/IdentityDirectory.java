package com.fleetpilot.vehicle.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The directory of the user accounts (Keycloak): who may sign in, with which role and attached to what. The
 * administration screens go through this port, so the rules stay testable without Keycloak.
 */
public interface IdentityDirectory {

    /** A user account as the directory knows it. */
    record Account(String id, String username, String email, String firstName, String lastName, boolean enabled,
            String role, Map<String, String> attributes, boolean federated, Instant createdAt) {

        public Optional<String> attribute(String name) {
            return Optional.ofNullable(attributes.get(name)).filter(value -> !value.isBlank());
        }
    }

    /** The fields of an account that the administration sets. */
    record AccountData(String username, String email, String firstName, String lastName, boolean enabled, String role,
            Map<String, String> attributes) {
    }

    /** Every account, service accounts left out. */
    List<Account> accounts();

    Optional<Account> account(String id);

    /** Accounts carrying an attribute value, such as the account of a driver. */
    List<Account> accountsWith(String attribute, String value);

    /** Creates the account with a temporary password the user must change at the first sign-in; returns its id. */
    String create(AccountData data, String temporaryPassword);

    void update(String id, AccountData data);

    /** Sets a temporary password the user must change at the next sign-in. */
    void resetPassword(String id, String temporaryPassword);
}
