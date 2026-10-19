package com.fleetpilot.bigdata.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.bigdata.streams.JsonSerde;
import com.fleetpilot.bigdata.streams.Model.VehicleRef;
import com.fleetpilot.bigdata.streams.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

/**
 * The vehicles of the fleet, read from the fleet module and published on the compacted topic
 * {@code reference.vehicles}: the topology joins every message with it (global table), the history loader enriches
 * the past days with it. Refreshed every hour, so that a new vehicle or a vehicle that changed company is followed.
 */
public class FleetReference {

    private static final Logger LOG = LoggerFactory.getLogger(FleetReference.class);
    private static final int PAGE_SIZE = 100;

    private final RestClient fleet;
    private final KafkaTemplate<String, String> kafka;
    private final Map<UUID, VehicleRef> vehicles = new ConcurrentHashMap<>();

    public FleetReference(RestClient fleet, KafkaTemplate<String, String> kafka) {
        this.fleet = fleet;
        this.kafka = kafka;
    }

    record Company(UUID id, String name) {
    }

    record Vehicle(UUID id, UUID companyId, String registration, String category, String make, String model,
            String energy) {
    }

    record VehiclePage(List<Vehicle> content, int totalPages) {
    }

    public VehicleRef vehicle(UUID id) {
        return vehicles.get(id);
    }

    public boolean isLoaded() {
        return !vehicles.isEmpty();
    }

    /** Every hour, and a few seconds after the start (retried until the fleet module answers). */
    @Scheduled(initialDelay = 5_000, fixedDelay = 3_600_000)
    public void refresh() {
        try {
            publish(load());
        } catch (RuntimeException e) {
            LOG.warn("Fleet reference not refreshed: {}", e.getMessage());
            if (vehicles.isEmpty()) {
                retrySoon();
            }
        }
    }

    /** The vehicles of the fleet with the name of their company. */
    protected List<VehicleRef> load() {
        Map<UUID, String> companies = fleet.get().uri("/api/v1/companies").retrieve()
                .body(new ParameterizedTypeReference<List<Company>>() { })
                .stream().collect(Collectors.toMap(Company::id, Company::name));
        List<VehicleRef> all = new ArrayList<>();
        int page = 0;
        VehiclePage current;
        do {
            int index = page++;
            current = fleet.get().uri(b -> b.path("/api/v1/vehicles").queryParam("page", index)
                    .queryParam("size", PAGE_SIZE).queryParam("sort", "registration").build())
                    .retrieve().body(VehiclePage.class);
            if (current != null) {
                current.content().forEach(v -> all.add(new VehicleRef(v.id(), v.companyId(),
                        companies.get(v.companyId()), v.registration(), v.category(), v.energy(), v.make(),
                        v.model())));
            }
        } while (current != null && page < current.totalPages());
        return all;
    }

    void publish(List<VehicleRef> refs) {
        refs.forEach(ref -> kafka.send(Topics.VEHICLES, ref.vehicleId().toString(), JsonSerde.write(ref)));
        kafka.flush();
        vehicles.putAll(refs.stream().collect(Collectors.toMap(VehicleRef::vehicleId, Function.identity())));
        LOG.info("Fleet reference published: {} vehicles", refs.size());
    }

    private void retrySoon() {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(10_000);
                refresh();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
