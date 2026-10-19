package com.fleetpilot.integration.fleet;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.web.client.RestClient;

/**
 * The fleet module read through its REST API with the identity of the bus. The vehicles are kept ten minutes: a fuel
 * card file of a thousand lines is checked against one copy of the fleet, not a thousand calls.
 */
public class RestFleetDirectory implements FleetDirectory {

    private static final int PAGE_SIZE = 100;
    private static final Duration KEEP = Duration.ofMinutes(10);

    private final RestClient rest;
    private final Clock clock;
    private volatile List<FleetVehicle> vehicles;
    private volatile Instant loadedAt = Instant.EPOCH;
    private final Map<String, List<LeaseContract>> contracts = new ConcurrentHashMap<>();

    public RestFleetDirectory(RestClient rest, Clock clock) {
        this.rest = rest;
        this.clock = clock;
    }

    record VehiclePage(List<FleetVehicle> content, int totalPages) {
    }

    record VehicleRef(UUID id, String registration) {
    }

    record ContractView(UUID id, VehicleRef vehicle, String lessor, String contractNumber, LocalDate startDate,
            LocalDate endDate, BigDecimal monthlyRent, String status, LocalDate endedOn) {
    }

    record ContractPage(List<ContractView> content, int totalPages) {
    }

    @Override
    public List<FleetVehicle> vehicles() {
        if (vehicles == null || loadedAt.plus(KEEP).isBefore(clock.instant())) {
            vehicles = List.copyOf(pages(page -> rest.get().uri(b -> b.path("/api/v1/vehicles")
                    .queryParam("page", page).queryParam("size", PAGE_SIZE).queryParam("sort", "registration").build())
                    .retrieve().body(VehiclePage.class), VehiclePage::content, VehiclePage::totalPages));
            loadedAt = clock.instant();
            contracts.clear();
        }
        return vehicles;
    }

    @Override
    public List<LeaseContract> contracts(String lessor) {
        return contracts.computeIfAbsent(lessor, name -> pages(page -> rest.get().uri(b -> b.path("/api/v1/contracts")
                        .queryParam("lessor", name).queryParam("page", page).queryParam("size", PAGE_SIZE)
                        // a unique sort key: paging on a non unique one may repeat or skip contracts between pages
                        .queryParam("sort", "contractNumber").build())
                        .retrieve().body(ContractPage.class), ContractPage::content, ContractPage::totalPages)
                .stream()
                .map(c -> new LeaseContract(c.id(), c.lessor(), c.contractNumber(), c.vehicle().id(),
                        c.vehicle().registration(), c.startDate(), c.endDate(), c.monthlyRent(), c.status(), c.endedOn()))
                .toList());
    }

    private static <P, T> List<T> pages(Function<Integer, P> fetch, Function<P, List<T>> content,
            Function<P, Integer> totalPages) {
        List<T> all = new ArrayList<>();
        int page = 0;
        P current;
        do {
            current = fetch.apply(page++);
            if (current != null) {
                all.addAll(content.apply(current));
            }
        } while (current != null && page < totalPages.apply(current));
        return all;
    }
}
