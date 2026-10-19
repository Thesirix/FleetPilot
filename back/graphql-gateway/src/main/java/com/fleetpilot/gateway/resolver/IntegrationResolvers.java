package com.fleetpilot.gateway.resolver;

import static com.fleetpilot.gateway.resolver.FleetQueries.orNull;
import static com.fleetpilot.gateway.resolver.FleetQueries.pageParams;
import static com.fleetpilot.gateway.resolver.FleetQueries.params;
import static org.springframework.http.HttpMethod.POST;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/** The integration bus: partner flows, journal of the exchanges, fuel card transactions. */
@Controller
public class IntegrationResolvers {

    private final ServiceClient integration;

    public IntegrationResolvers(ServiceClient integrationService) {
        this.integration = integrationService;
    }

    @QueryMapping
    public Object integrationFlows() {
        return integration.getList("/api/v1/integration/flows");
    }

    @QueryMapping
    public Object exchanges(@Argument String flow, @Argument String status, @Argument Integer page,
            @Argument Integer size) {
        return integration.get("/api/v1/integration/exchanges",
                pageParams(params("flow", flow, "status", status), page, size, null));
    }

    @QueryMapping
    public Object exchange(@Argument String id) {
        return orNull(() -> integration.get("/api/v1/integration/exchanges/{id}", Map.of(), id));
    }

    @MutationMapping
    public Object replayExchange(@Argument String id) {
        return integration.send(POST, "/api/v1/integration/exchanges/{id}/replay", null, id);
    }

    @QueryMapping
    public Object fuelTransactions(@Argument Map<String, Object> filter, @Argument Integer page,
            @Argument Integer size) {
        return integration.get("/api/v1/fuel/transactions", pageParams(filter, page, size, null));
    }

    @QueryMapping
    public Object fuelSummary(@Argument String companyId, @Argument String from, @Argument String to) {
        return integration.get("/api/v1/fuel/summary", params("companyId", companyId, "from", from, "to", to));
    }

    /** The bus stores the anomalies of a transaction as a comma separated list of codes. */
    @SchemaMapping(typeName = "FuelTransaction", field = "anomalies")
    public List<String> anomalies(Map<String, Object> transaction) {
        Object codes = transaction.get("anomalies");
        return codes instanceof String text && !text.isBlank() ? Arrays.asList(text.split(",")) : List.of();
    }

    /** The anomalies of the period by type, most frequent first. */
    @SchemaMapping(typeName = "FuelSummary", field = "anomaliesByType")
    public List<Map<String, Object>> anomaliesByType(Map<String, Object> summary) {
        if (!(summary.get("anomaliesByType") instanceof Map<?, ?> byType)) {
            return List.of();
        }
        return byType.entrySet().stream()
                .map(e -> Map.<String, Object>of("key", e.getKey(), "count", e.getValue()))
                .sorted(Comparator.comparingInt(m -> -((Number) m.get("count")).intValue()))
                .toList();
    }
}
