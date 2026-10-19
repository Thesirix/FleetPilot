package com.fleetpilot.portal.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The fleet API (vehicle-service) as the extranet uses it, with the token of the signed-in lessor: the service only
 * returns the contracts of that lessor and only accepts readings for the vehicles it leases. In v1 the extranet read
 * its own copy of the contracts, refreshed every night; it now reads the reference data.
 */
public class FleetApi {

    /** A failed call: the HTTP status, the stable code of the service (MILEAGE_DECREASE...) and its message. */
    public static class ApiException extends Exception {
        private final int status;
        private final String code;

        public ApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }
    }

    /** Page size of the API (its maximum): a lessor has a few hundred contracts at most, read in a few calls. */
    static final int PAGE_SIZE = 100;

    private final URI base;
    private final HttpClient http;
    private final JsonMapper json;

    public FleetApi(URI base, HttpClient http, JsonMapper json) {
        this.base = base;
        this.http = http;
        this.json = json;
    }

    /** The active contracts of the lessor of the token, ending first first. */
    public List<Contract> activeContracts(String token) throws ApiException {
        List<Contract> contracts = new ArrayList<>();
        int pages = 1;
        for (int page = 0; page < pages; page++) {
            JsonNode result = call(token, "GET", "api/v1/contracts?status=ACTIVE&size=" + PAGE_SIZE + "&page=" + page
                    + "&sort=endDate,asc&sort=contractNumber,asc", null);
            result.path("content").forEach(node -> contracts.add(Contract.of(node)));
            pages = result.path("totalPages").asInt(1);
        }
        return contracts;
    }

    public Contract contract(String token, UUID id) throws ApiException {
        return Contract.of(call(token, "GET", "api/v1/contracts/" + id, null));
    }

    /** The last odometer readings of the vehicle, newest first. */
    public List<Reading> readings(String token, UUID vehicleId) throws ApiException {
        List<Reading> readings = new ArrayList<>();
        call(token, "GET", "api/v1/vehicles/" + vehicleId + "/mileage", null)
                .forEach(node -> readings.add(Reading.of(node)));
        return readings;
    }

    /** A reading at the return of the vehicle, recorded with the lessor as source. */
    public void recordReading(String token, UUID vehicleId, int mileage, Instant readAt) throws ApiException {
        String body = json.writeValueAsString(Map.of("mileage", mileage, "readAt", readAt.toString(), "source", "LESSOR"));
        call(token, "POST", "api/v1/vehicles/" + vehicleId + "/mileage", body);
    }

    private JsonNode call(String token, String method, String path, String body) throws ApiException {
        HttpRequest.Builder request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Accept-Language", "fr-FR");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new ApiException(503, "SERVICE_UNAVAILABLE", "Fleet API unreachable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "SERVICE_UNAVAILABLE", "Interrupted");
        }
        if (response.statusCode() >= 400) {
            JsonNode problem = response.body().isBlank() ? json.createObjectNode() : parse(response.body());
            throw new ApiException(response.statusCode(), problem.path("code").asString("HTTP_" + response.statusCode()),
                    problem.path("detail").asString("HTTP " + response.statusCode()));
        }
        return response.body().isBlank() ? json.createObjectNode() : parse(response.body());
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (RuntimeException notJson) {
            return json.createObjectNode();
        }
    }
}
