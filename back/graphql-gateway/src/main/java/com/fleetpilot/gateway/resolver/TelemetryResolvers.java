package com.fleetpilot.gateway.resolver;

import static com.fleetpilot.gateway.resolver.FleetQueries.orNull;
import static com.fleetpilot.gateway.resolver.FleetQueries.pageParams;
import static com.fleetpilot.gateway.resolver.FleetQueries.params;
import static org.springframework.http.HttpMethod.POST;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** Connected vehicles and alerts: one call to the telemetry service each. */
@Controller
public class TelemetryResolvers {

    private final ServiceClient telemetry;

    public TelemetryResolvers(ServiceClient telemetryService) {
        this.telemetry = telemetryService;
    }

    @QueryMapping
    public Object telemetrySummary(@Argument String companyId) {
        return telemetry.get("/api/v1/telemetry/summary", params("companyId", companyId));
    }

    /** The service returns the open alerts by type as a map; GraphQL gets a list, most frequent first. */
    @SchemaMapping(typeName = "TelemetrySummary", field = "openAlertsByType")
    public List<Map<String, Object>> openAlertsByType(Map<String, Object> summary) {
        if (!(summary.get("openAlertsByType") instanceof Map<?, ?> byType)) {
            return List.of();
        }
        return byType.entrySet().stream()
                .map(e -> Map.<String, Object>of("type", e.getKey(), "count", e.getValue()))
                .sorted(Comparator.comparingInt(m -> -((Number) m.get("count")).intValue()))
                .toList();
    }

    @QueryMapping
    public Object liveVehicles(@Argument String companyId) {
        return telemetry.getListWithQuery("/api/v1/telemetry/live", params("companyId", companyId));
    }

    @QueryMapping
    public Object vehicleTelemetry(@Argument String vehicleId) {
        return orNull(() -> telemetry.get("/api/v1/telemetry/vehicles/{id}", Map.of(), vehicleId));
    }

    @QueryMapping
    public Object usageProfiles(@Argument String companyId) {
        return telemetry.getListWithQuery("/api/v1/telemetry/usage-profiles", params("companyId", companyId));
    }

    @QueryMapping
    public Object alerts(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size) {
        return telemetry.get("/api/v1/alerts", pageParams(filter, page, size, null));
    }

    @MutationMapping
    public Object acknowledgeAlert(@Argument String id) {
        return telemetry.send(POST, "/api/v1/alerts/{id}/acknowledge", null, id);
    }

    @MutationMapping
    public Object resolveAlert(@Argument String id, @Argument String comment) {
        return telemetry.send(POST, "/api/v1/alerts/{id}/resolve", params("comment", comment), id);
    }
}
