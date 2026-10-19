package com.fleetpilot.gateway.dataloader;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

/**
 * Resolves {@code vehicleSummary} on deadlines and claims with a DataLoader: for a page of 50 deadlines, the gateway
 * makes one call to the vehicle service ({@code GET /vehicles?ids=...}) instead of 50 (the N+1 problem).
 */
@Controller
public class VehicleSummaryLoader {

    private final ServiceClient vehicles;

    public VehicleSummaryLoader(ServiceClient vehicleService) {
        this.vehicles = vehicleService;
    }

    @BatchMapping(typeName = "Deadline", field = "vehicleSummary")
    public Map<Map<String, Object>, Object> deadlineVehicles(List<Map<String, Object>> deadlines) {
        return load(deadlines);
    }

    @BatchMapping(typeName = "Incident", field = "vehicleSummary")
    public Map<Map<String, Object>, Object> incidentVehicles(List<Map<String, Object>> incidents) {
        return load(incidents);
    }

    @SuppressWarnings("unchecked")
    private Map<Map<String, Object>, Object> load(List<Map<String, Object>> sources) {
        List<Object> ids = sources.stream().map(VehicleSummaryLoader::vehicleId).distinct().toList();
        Map<String, Object> page = vehicles.getMap("/api/v1/vehicles", Map.of("ids", ids, "size", Math.max(ids.size(), 1)));
        Map<Object, Object> byId = ((List<Map<String, Object>>) page.get("content")).stream()
                .collect(Collectors.toMap(vehicle -> vehicle.get("id"), Function.identity()));
        Map<Map<String, Object>, Object> result = new LinkedHashMap<>();
        sources.forEach(source -> {
            Object summary = byId.get(vehicleId(source));
            if (summary != null) {
                result.put(source, summary);
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Object vehicleId(Map<String, Object> source) {
        return ((Map<String, Object>) source.get("vehicle")).get("id");
    }
}
