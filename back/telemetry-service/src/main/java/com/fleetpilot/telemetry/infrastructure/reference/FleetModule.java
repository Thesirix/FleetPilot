package com.fleetpilot.telemetry.infrastructure.reference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.web.client.RestClient;

/** The fleet module read page by page through its REST API, with the identity of the telemetry service. */
public class FleetModule {

    private static final int PAGE_SIZE = 100;

    /** A vehicle of the fleet, with the fields the telemetry needs. */
    public record Vehicle(UUID id, UUID companyId, String registration, String category, String energy, String status,
            int mileage) {
    }

    record VehicleRef(UUID id) {
    }

    /** A service deadline (by mileage). */
    public record Deadline(VehicleRef vehicle, String type, LocalDate dueDate, Integer dueMileage, String status) {
    }

    private final RestClient rest;

    public FleetModule(RestClient rest) {
        this.rest = rest;
    }

    public List<Vehicle> vehicles() {
        List<Vehicle> all = new ArrayList<>();
        int page = 0;
        VehiclePage current;
        do {
            int index = page++;
            current = rest.get().uri(b -> b.path("/api/v1/vehicles").queryParam("page", index)
                    .queryParam("size", PAGE_SIZE).queryParam("sort", "registration").build())
                    .retrieve().body(VehiclePage.class);
            if (current != null) {
                all.addAll(current.content());
            }
        } while (current != null && page < current.totalPages());
        return all;
    }

    /** Mileage of the next service of each vehicle (open SERVICE deadlines). */
    public List<ServiceDue> servicesDue() {
        List<ServiceDue> all = new ArrayList<>();
        int page = 0;
        DeadlinePage current;
        do {
            int index = page++;
            current = rest.get().uri(b -> b.path("/api/v1/deadlines").queryParam("page", index)
                    .queryParam("size", PAGE_SIZE).queryParam("type", "SERVICE").build())
                    .retrieve().body(DeadlinePage.class);
            if (current != null) {
                current.content().stream()
                        .filter(d -> d.dueMileage() != null && d.vehicle() != null && "OPEN".equals(d.status()))
                        .forEach(d -> all.add(new ServiceDue(d.vehicle().id(), d.dueMileage())));
            }
        } while (current != null && page < current.totalPages());
        return all;
    }

    public record ServiceDue(UUID vehicleId, int dueMileage) {
    }

    record VehiclePage(List<Vehicle> content, int totalPages) {
    }

    record DeadlinePage(List<Deadline> content, int totalPages) {
    }
}
