package com.fleetpilot.gateway.resolver;

import static com.fleetpilot.gateway.resolver.FleetQueries.orNull;
import static com.fleetpilot.gateway.resolver.FleetQueries.pageParams;
import static com.fleetpilot.gateway.resolver.FleetQueries.params;
import static org.springframework.http.HttpMethod.POST;

import java.util.List;
import java.util.Map;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Reports, indicators and the journal of the e-mails: one call to the reporting or notification service each. */
@Controller
public class OperationsResolvers {

    private final ServiceClient reporting;
    private final ServiceClient notifications;

    public OperationsResolvers(ServiceClient reportingService, ServiceClient notificationService) {
        this.reporting = reportingService;
        this.notifications = notificationService;
    }

    @QueryMapping
    public Object indicators(@Argument String companyId) {
        return reporting.get("/api/v1/indicators", params("companyId", companyId));
    }

    @QueryMapping
    public Object reportTypes() {
        return reporting.getList("/api/v1/reports/types");
    }

    @QueryMapping
    public Object reports(@Argument Integer page, @Argument Integer size) {
        return reporting.get("/api/v1/reports", pageParams(Map.of(), page, size, List.of("requestedAt,desc")));
    }

    @QueryMapping
    public Object report(@Argument String id) {
        return orNull(() -> reporting.get("/api/v1/reports/{id}", Map.of(), id));
    }

    @MutationMapping
    public Object requestReport(@Argument String type, @Argument String companyId, @Argument String from,
            @Argument String to) {
        return reporting.send(POST, "/api/v1/reports", params("type", type, "companyId", companyId, "from", from,
                "to", to));
    }

    @QueryMapping
    public Object notifications(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size) {
        return notifications.get("/api/v1/notifications", pageParams(filter, page, size, null));
    }

    @QueryMapping
    public Object notification(@Argument String id) {
        return orNull(() -> notifications.get("/api/v1/notifications/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object notificationStats() {
        return notifications.get("/api/v1/notifications/stats", Map.of());
    }

    @MutationMapping
    public Object retryNotification(@Argument String id) {
        return notifications.send(POST, "/api/v1/notifications/{id}/retry", null, id);
    }
}
