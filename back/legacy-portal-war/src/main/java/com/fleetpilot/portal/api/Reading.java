package com.fleetpilot.portal.api;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/** An odometer reading of the vehicle and who made it (driver, connected box, garage, manager, lessor...). */
public record Reading(int mileage, Instant readAt, String source) {

    static Reading of(JsonNode n) {
        return new Reading(n.path("mileage").asInt(), Instant.parse(n.path("readAt").asString()),
                n.path("source").asString());
    }
}
