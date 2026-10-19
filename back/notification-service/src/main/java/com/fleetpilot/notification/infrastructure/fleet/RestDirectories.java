package com.fleetpilot.notification.infrastructure.fleet;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.notification.application.Ports.FleetDirectory;
import com.fleetpilot.notification.application.Ports.Person;
import com.fleetpilot.notification.application.Ports.QuoteDirectory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** The fleet and quote modules, read through their REST APIs with the identity of the notification service. */
public final class RestDirectories {

    private RestDirectories() {
    }

    /** Only the fields needed here; the others are ignored. */
    record DriverRef(UUID id) {
    }

    record Assignment(DriverRef driver) {
    }

    record Vehicle(Assignment currentAssignment) {
    }

    record DriverSummary(String fullName, String email) {
    }

    record DriverFile(DriverSummary driver) {
    }

    public static final class Fleet implements FleetDirectory {

        private final RestClient rest;

        public Fleet(RestClient rest) {
            this.rest = rest;
        }

        @Override
        public Optional<Person> currentDriver(UUID vehicleId) {
            return get("/api/v1/vehicles/{id}", Vehicle.class, vehicleId)
                    .map(Vehicle::currentAssignment)
                    .map(Assignment::driver)
                    .flatMap(driver -> driver(driver.id()));
        }

        @Override
        public Optional<Person> driver(UUID driverId) {
            return get("/api/v1/drivers/{id}", DriverFile.class, driverId)
                    .map(DriverFile::driver)
                    .filter(d -> d.email() != null && !d.email().isBlank())
                    .map(d -> new Person(d.fullName(), d.email()));
        }

        private <T> Optional<T> get(String path, Class<T> type, UUID id) {
            try {
                return Optional.ofNullable(rest.get().uri(path, id).retrieve().body(type));
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
        }
    }

    public static final class Quotes implements QuoteDirectory {

        private final RestClient rest;

        public Quotes(RestClient rest) {
            this.rest = rest;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> quote(UUID quoteId) {
            return rest.get().uri("/api/v1/quotes/{id}", quoteId).retrieve().body(Map.class);
        }
    }
}
