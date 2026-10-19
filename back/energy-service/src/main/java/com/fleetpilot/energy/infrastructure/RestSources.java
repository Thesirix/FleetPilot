package com.fleetpilot.energy.infrastructure;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.energy.application.Sources.FleetData;
import com.fleetpilot.energy.application.Sources.UsageData;
import com.fleetpilot.energy.domain.Transition.Usage;
import com.fleetpilot.energy.domain.Transition.VehicleFacts;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/** The fleet module and the telemetry service read over REST, with the identity of the energy service. */
public class RestSources implements FleetData, UsageData {

    private static final int PAGE_SIZE = 100;
    private static final Set<String> OUT_OF_FLEET = Set.of("DISPOSED", "AWAITING_DELIVERY");

    private final RestClient fleet;
    private final RestClient telemetry;
    private final Clock clock;

    public RestSources(RestClient fleet, RestClient telemetry, Clock clock) {
        this.fleet = fleet;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    record Company(UUID id, String name) {
    }

    record Site(String city) {
    }

    record Vehicle(UUID id, UUID companyId, String registration, String category, String make, String model,
            String energy, int critAir, int mileage, String status, Site site, int co2GramsPerKm,
            LocalDate firstRegistrationDate, String acquisition) {
    }

    record VehicleRef(UUID id) {
    }

    record Contract(VehicleRef vehicle, LocalDate endDate, BigDecimal monthlyRent, String status) {
    }

    record Page<T>(List<T> content, int totalPages) {
    }

    record UsageDay(UUID vehicleId, LocalDate day, BigDecimal distanceKm) {
    }

    @Override
    public List<VehicleFacts> vehicles() {
        Map<UUID, String> companies = fleet.get().uri("/api/v1/companies").retrieve()
                .body(new ParameterizedTypeReference<List<Company>>() { })
                .stream().collect(Collectors.toMap(Company::id, Company::name));
        Map<UUID, Contract> leases = pages(page -> fleet.get().uri(b -> b.path("/api/v1/contracts")
                        .queryParam("status", "ACTIVE").queryParam("page", page).queryParam("size", PAGE_SIZE)
                        .queryParam("sort", "contractNumber").build())
                .retrieve().body(new ParameterizedTypeReference<Page<Contract>>() { }))
                .stream().collect(Collectors.toMap(c -> c.vehicle().id(), Function.identity(), (a, b) -> a));
        return pages(page -> fleet.get().uri(b -> b.path("/api/v1/vehicles").queryParam("page", page)
                        .queryParam("size", PAGE_SIZE).queryParam("sort", "registration").build())
                .retrieve().body(new ParameterizedTypeReference<Page<Vehicle>>() { }))
                .stream()
                .filter(v -> !OUT_OF_FLEET.contains(v.status()))
                .map(v -> {
                    Contract lease = leases.get(v.id());
                    return new VehicleFacts(v.id(), v.companyId(), companies.get(v.companyId()), v.registration(),
                            v.make(), v.model(), v.category(), v.energy(), v.critAir(), v.co2GramsPerKm(),
                            v.site() == null ? null : v.site().city(), v.mileage(), v.firstRegistrationDate(),
                            v.acquisition(), lease == null ? null : lease.endDate(),
                            lease == null ? null : lease.monthlyRent());
                })
                .toList();
    }

    @Override
    public Map<UUID, Usage> usage(int days) {
        LocalDate from = LocalDate.now(clock).minusDays(days);
        Map<UUID, List<BigDecimal>> distances = new HashMap<>();
        for (UsageDay day : pages(page -> telemetry.get().uri(b -> b.path("/api/v1/telemetry/usage-days")
                        .queryParam("from", from).queryParam("page", page).queryParam("size", 5_000).build())
                .retrieve().body(new ParameterizedTypeReference<Page<UsageDay>>() { }))) {
            if (day.distanceKm() != null && day.distanceKm().signum() > 0 && day.day().isBefore(LocalDate.now(clock))) {
                distances.computeIfAbsent(day.vehicleId(), id -> new ArrayList<>()).add(day.distanceKm());
            }
        }
        return distances.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new Usage(days, e.getValue())));
    }

    private static <T> List<T> pages(Function<Integer, Page<T>> fetch) {
        List<T> all = new ArrayList<>();
        int page = 0;
        Page<T> current;
        do {
            current = fetch.apply(page++);
            if (current != null) {
                all.addAll(current.content());
            }
        } while (current != null && page < current.totalPages());
        return all;
    }
}
