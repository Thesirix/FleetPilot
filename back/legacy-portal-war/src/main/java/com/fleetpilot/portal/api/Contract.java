package com.fleetpilot.portal.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * A lease contract as the lessor follows it: the vehicle, the term, the mileage agreed and the mileage projected at
 * the end of the contract from the real use of the vehicle (an overrun is invoiced to the client at the return).
 */
public record Contract(UUID id, String contractNumber, UUID vehicleId, String registration, String make, String model,
        LocalDate startDate, LocalDate endDate, int durationMonths, int contractualMileage, int startMileage,
        BigDecimal monthlyRent, boolean maintenanceIncluded, boolean tyresIncluded, boolean replacementVehicle,
        Long daysRemaining, Integer projectedMileage, Integer projectedOverrunKm, boolean overrunRisk) {

    static Contract of(JsonNode n) {
        JsonNode vehicle = n.path("vehicle");
        JsonNode projection = n.path("projection");
        boolean projected = projection.isObject();
        return new Contract(
                UUID.fromString(n.path("id").asString()),
                n.path("contractNumber").asString(),
                UUID.fromString(vehicle.path("id").asString()),
                vehicle.path("registration").asString(),
                vehicle.path("make").asString(),
                vehicle.path("model").asString(),
                LocalDate.parse(n.path("startDate").asString()),
                LocalDate.parse(n.path("endDate").asString()),
                n.path("durationMonths").asInt(),
                n.path("contractualMileage").asInt(),
                n.path("startMileage").asInt(),
                n.path("monthlyRent").decimalValue(),
                n.path("maintenanceIncluded").asBoolean(),
                n.path("tyresIncluded").asBoolean(),
                n.path("replacementVehicle").asBoolean(),
                n.path("daysRemaining").isNumber() ? n.path("daysRemaining").asLong() : null,
                projected ? projection.path("projectedMileage").asInt() : null,
                projected ? projection.path("projectedOverrunKm").asInt() : null,
                projected && projection.path("overrunRisk").asBoolean());
    }

    /** Ends within the given number of days (the returns to prepare). */
    public boolean endsWithin(int days) {
        return daysRemaining != null && daysRemaining <= days;
    }

    /** Matches a registration or a contract number, whatever the spaces, dashes and case. */
    public boolean matches(String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String wanted = normalize(search);
        return normalize(registration).contains(wanted) || normalize(contractNumber).contains(wanted);
    }

    private static String normalize(String value) {
        return value.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }
}
