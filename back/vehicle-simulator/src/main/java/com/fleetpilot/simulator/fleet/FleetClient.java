package com.fleetpilot.simulator.fleet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fleetpilot.simulator.engine.FleetSelection.FleetVehicle;
import org.springframework.web.client.RestClient;

/** Reads the vehicles of every client company from the fleet module, page by page, with the simulator's identity. */
public class FleetClient {

    private static final int PAGE_SIZE = 100;

    record Site(String name) {
    }

    record Vehicle(UUID id, UUID companyId, String registration, String category, String energy, String status,
            Site site, int mileage) {
    }

    record Page(List<Vehicle> content, int totalPages) {
    }

    private final RestClient rest;

    public FleetClient(RestClient rest) {
        this.rest = rest;
    }

    public List<FleetVehicle> vehicles() {
        List<FleetVehicle> all = new ArrayList<>();
        int page = 0;
        Page current;
        do {
            int index = page++;
            current = rest.get().uri(builder -> builder.path("/api/v1/vehicles").queryParam("page", index)
                    .queryParam("size", PAGE_SIZE).queryParam("sort", "registration").build()).retrieve().body(Page.class);
            if (current != null) {
                current.content().forEach(v -> all.add(new FleetVehicle(v.id(), v.companyId(), v.registration(),
                        v.category(), v.energy(), v.status(), v.site() == null ? null : v.site().name(), v.mileage())));
            }
        } while (current != null && page < current.totalPages());
        return all;
    }
}
