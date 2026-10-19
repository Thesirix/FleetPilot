package com.fleetpilot.portal.web;

import java.util.UUID;

/** Ids of the URLs and forms: a malformed one is simply not found. */
final class Ids {

    private Ids() {
    }

    static UUID parse(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
