package com.fleetpilot.gateway.resolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.HttpClientErrorException;

/** Read side of the fleet module: queries and the fields resolved with an extra call to the vehicle service. */
@Controller
public class FleetQueries {

    private final ServiceClient vehicles;

    public FleetQueries(ServiceClient vehicleService) {
        this.vehicles = vehicleService;
    }

    // ---------------------------------------------------------------- companies and indicators

    @QueryMapping
    public Object companies() {
        return vehicles.getList("/api/v1/companies");
    }

    @QueryMapping
    public Object company(@Argument String id) {
        return orNull(() -> vehicles.get("/api/v1/companies/{id}", Map.of(), id));
    }

    @SchemaMapping(typeName = "Company", field = "sites")
    public Object sites(Map<String, Object> company) {
        return vehicles.getList("/api/v1/companies/{id}/sites", company.get("id"));
    }

    @SchemaMapping(typeName = "Company", field = "costCenters")
    public Object costCenters(Map<String, Object> company) {
        return vehicles.getList("/api/v1/companies/{id}/cost-centers", company.get("id"));
    }

    /** The service returns counts as JSON objects ({"DIESEL": 672}); GraphQL exposes ordered lists of key and count. */
    @QueryMapping
    public Object fleetSummary(@Argument String companyId) {
        Map<String, Object> summary = new LinkedHashMap<>(vehicles.getMap("/api/v1/fleet/summary",
                params("companyId", companyId)));
        for (String breakdown : List.of("byStatus", "byCategory", "byEnergy", "byCritAir")) {
            summary.put(breakdown, counts(summary.get(breakdown)));
        }
        return summary;
    }

    // ---------------------------------------------------------------- lists

    @QueryMapping
    public Object vehicles(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size,
            @Argument List<String> sort) {
        return vehicles.get("/api/v1/vehicles", pageParams(filter, page, size, sort));
    }

    @QueryMapping
    public Object drivers(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size,
            @Argument List<String> sort) {
        return vehicles.get("/api/v1/drivers", pageParams(filter, page, size, sort));
    }

    @QueryMapping
    public Object contracts(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size,
            @Argument List<String> sort) {
        return vehicles.get("/api/v1/contracts", pageParams(filter, page, size, sort));
    }

    @QueryMapping
    public Object deadlines(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size,
            @Argument List<String> sort) {
        return vehicles.get("/api/v1/deadlines", pageParams(filter, page, size, sort));
    }

    @QueryMapping
    public Object incidents(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size,
            @Argument List<String> sort) {
        return vehicles.get("/api/v1/incidents", pageParams(filter, page, size, sort));
    }

    // ---------------------------------------------------------------- files

    @QueryMapping
    public Object vehicle(@Argument String id) {
        return orNull(() -> vehicles.get("/api/v1/vehicles/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object vehicleByRegistration(@Argument String registration) {
        return orNull(() -> vehicles.get("/api/v1/vehicles/by-registration/{registration}", Map.of(), registration));
    }

    @QueryMapping
    public Object driver(@Argument String id) {
        return orNull(() -> vehicles.get("/api/v1/drivers/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object contract(@Argument String id) {
        return orNull(() -> vehicles.get("/api/v1/contracts/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object incident(@Argument String id) {
        return orNull(() -> vehicles.get("/api/v1/incidents/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object mySpace() {
        return vehicles.get("/api/v1/me", Map.of());
    }

    @SchemaMapping(typeName = "Vehicle", field = "assignments")
    public Object assignments(Map<String, Object> vehicle) {
        return vehicles.getList("/api/v1/vehicles/{id}/assignments", vehicle.get("id"));
    }

    @SchemaMapping(typeName = "Vehicle", field = "contracts")
    public Object vehicleContracts(Map<String, Object> vehicle) {
        return vehicles.getList("/api/v1/vehicles/{id}/contracts", vehicle.get("id"));
    }

    @SchemaMapping(typeName = "Vehicle", field = "incidents")
    public Object vehicleIncidents(Map<String, Object> vehicle) {
        return vehicles.getMap("/api/v1/incidents", params("vehicleId", vehicle.get("id"), "size", 100)).get("content");
    }

    // ---------------------------------------------------------------- helpers

    /** A record that does not exist, or is not visible to the user, is a null field rather than an error. */
    static Object orNull(Supplier<Object> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    static Map<String, Object> pageParams(Map<String, Object> filter, Integer page, Integer size, List<String> sort) {
        Map<String, Object> params = new HashMap<>(filter == null ? Map.of() : filter);
        params.put("page", page == null ? 0 : page);
        params.put("size", size == null ? 20 : size);
        if (sort != null) {
            params.put("sort", sort);
        }
        return params;
    }

    static Map<String, Object> params(Object... keysAndValues) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            params.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return params;
    }

    static List<Map<String, Object>> counts(Object values) {
        List<Map<String, Object>> counts = new ArrayList<>();
        if (values instanceof Map<?, ?> map) {
            map.forEach((key, count) -> counts.add(Map.of("key", String.valueOf(key), "count", count)));
        }
        return counts;
    }
}
