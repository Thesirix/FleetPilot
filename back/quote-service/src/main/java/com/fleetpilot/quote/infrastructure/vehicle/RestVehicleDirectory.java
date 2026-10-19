package com.fleetpilot.quote.infrastructure.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fleetpilot.quote.application.VehicleDirectory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Reads the vehicles of the fleet module through its REST API. The {@link RestClient} given here authenticates with
 * the {@code client_credentials} grant: the quote service calls as itself, never with a user token.
 */
public class RestVehicleDirectory implements VehicleDirectory {

    private static final int PAGE_SIZE = 100;

    /** The fields of the fleet API this service needs; the others are ignored. */
    record Site(String city) {
    }

    record Vehicle(UUID id, UUID companyId, String registration, String make, String model, String category,
            String energy, Site site) {

        RemoteVehicle toRemote() {
            return new RemoteVehicle(id, companyId, registration, make, model, category, energy,
                    site == null ? null : site.city());
        }
    }

    record VehicleDetail(UUID id, Company company, String registration, String make, String model, String category,
            String energy, Site site) {

        RemoteVehicle toRemote() {
            return new RemoteVehicle(id, company.id(), registration, make, model, category, energy,
                    site == null ? null : site.city());
        }
    }

    record Company(UUID id) {
    }

    record Page(List<Vehicle> content, int totalPages) {
    }

    private final RestClient rest;

    public RestVehicleDirectory(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Optional<RemoteVehicle> findByRegistration(String registration) {
        try {
            VehicleDetail detail = rest.get().uri("/api/v1/vehicles/by-registration/{registration}", registration)
                    .retrieve().body(VehicleDetail.class);
            return Optional.ofNullable(detail).map(VehicleDetail::toRemote);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public List<RemoteVehicle> findAll() {
        List<RemoteVehicle> all = new ArrayList<>();
        int page = 0;
        Page current;
        do {
            int index = page++;
            current = rest.get()
                    .uri(builder -> builder.path("/api/v1/vehicles").queryParam("page", index)
                            .queryParam("size", PAGE_SIZE).queryParam("sort", "registration").build())
                    .retrieve().body(Page.class);
            if (current != null) {
                current.content().stream().map(Vehicle::toRemote).forEach(all::add);
            }
        } while (current != null && page < current.totalPages());
        return all;
    }
}
