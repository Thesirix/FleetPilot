package com.fleetpilot.gateway.resolver;

import static com.fleetpilot.gateway.resolver.FleetQueries.orNull;
import static com.fleetpilot.gateway.resolver.FleetQueries.pageParams;
import static com.fleetpilot.gateway.resolver.FleetQueries.params;
import static org.springframework.http.HttpMethod.POST;

import java.util.HashMap;
import java.util.Map;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** The energy transition: one call to the energy service each. */
@Controller
public class EnergyResolvers {

    private final ServiceClient energy;

    public EnergyResolvers(ServiceClient energyService) {
        this.energy = energyService;
    }

    @QueryMapping
    public Object energySummary(@Argument String companyId) {
        return energy.get("/api/v1/energy/summary", params("companyId", companyId));
    }

    @QueryMapping
    public Object energyAssessments(@Argument Map<String, Object> filter, @Argument String sort, @Argument Integer page,
            @Argument Integer size) {
        Map<String, Object> query = new HashMap<>(filter == null ? Map.of() : filter);
        if (sort != null) {
            query.put("sort", sort);
        }
        return energy.get("/api/v1/energy/assessments", pageParams(query, page, size, null));
    }

    @QueryMapping
    public Object energyAssessment(@Argument String vehicleId) {
        return orNull(() -> energy.get("/api/v1/energy/assessments/{id}", Map.of(), vehicleId));
    }

    @QueryMapping
    public Object energyPlan(@Argument String companyId) {
        return energy.get("/api/v1/energy/plan", params("companyId", companyId));
    }

    @QueryMapping
    public Object evCatalog() {
        return energy.getList("/api/v1/energy/catalog");
    }

    @MutationMapping
    @SuppressWarnings("unchecked")
    public Object refreshEnergyAssessments() {
        return ((Map<String, Object>) energy.send(POST, "/api/v1/energy/assessments/refresh", null)).get("vehicles");
    }
}
