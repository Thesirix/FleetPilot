package com.fleetpilot.gateway.resolver;

import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.util.Map;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

/** Write side of the fleet module: each mutation is one call to the vehicle service, which validates and decides. */
@Controller
public class FleetMutations {

    private final ServiceClient vehicles;

    public FleetMutations(ServiceClient vehicleService) {
        this.vehicles = vehicleService;
    }

    @MutationMapping
    public Object createVehicle(@Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/vehicles", input);
    }

    @MutationMapping
    public Object updateVehicle(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/vehicles/{id}", input, id);
    }

    @MutationMapping
    public Object changeVehicleStatus(@Argument String id, @Argument String status) {
        return vehicles.send(PATCH, "/api/v1/vehicles/{id}/status", Map.of("status", status), id);
    }

    @MutationMapping
    public Object recordMileage(@Argument String id, @Argument Integer mileage, @Argument String readAt) {
        return vehicles.send(POST, "/api/v1/vehicles/{id}/mileage",
                FleetQueries.params("mileage", mileage, "readAt", readAt), id);
    }

    @MutationMapping
    public Object createDriver(@Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/drivers", input);
    }

    @MutationMapping
    public Object updateDriver(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/drivers/{id}", input, id);
    }

    @MutationMapping
    public Object assignVehicle(@Argument String vehicleId, @Argument String driverId, @Argument String kind,
            @Argument String startDate) {
        return vehicles.send(POST, "/api/v1/vehicles/{id}/assignments",
                FleetQueries.params("driverId", driverId, "kind", kind, "startDate", startDate), vehicleId);
    }

    @MutationMapping
    public Object endAssignment(@Argument String id, @Argument String endDate, @Argument Integer endMileage) {
        return vehicles.send(POST, "/api/v1/assignments/{id}/end",
                FleetQueries.params("endDate", endDate, "endMileage", endMileage), id);
    }

    @MutationMapping
    public Object createContract(@Argument String vehicleId, @Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/vehicles/{id}/contracts", input, vehicleId);
    }

    @MutationMapping
    public Object endContract(@Argument String id, @Argument String endedOn) {
        return vehicles.send(POST, "/api/v1/contracts/{id}/end", FleetQueries.params("endedOn", endedOn), id);
    }

    @MutationMapping
    public Object completeDeadline(@Argument String id, @Argument String completedOn, @Argument Integer mileage) {
        return vehicles.send(POST, "/api/v1/deadlines/{id}/complete",
                FleetQueries.params("completedOn", completedOn, "mileage", mileage), id);
    }

    @MutationMapping
    public Object declareIncident(@Argument String vehicleId, @Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/vehicles/{id}/incidents", input, vehicleId);
    }

    @MutationMapping
    public Object assessIncident(@Argument String id, @Argument String responsibility, @Argument Double estimatedCost) {
        return vehicles.send(PUT, "/api/v1/incidents/{id}/assessment",
                FleetQueries.params("responsibility", responsibility, "estimatedCost", estimatedCost), id);
    }

    @MutationMapping
    public Object changeIncidentStatus(@Argument String id, @Argument String status) {
        return vehicles.send(PATCH, "/api/v1/incidents/{id}/status", Map.of("status", status), id);
    }

    @MutationMapping
    public Object reportMyMileage(@Argument Integer mileage) {
        return vehicles.send(POST, "/api/v1/me/mileage", Map.of("mileage", mileage));
    }

    @MutationMapping
    public Object declareMyIncident(@Argument Map<String, Object> input) {
        return vehicles.send(POST, "/api/v1/me/incidents", input);
    }

    @MutationMapping
    public Object runDailySweep() {
        return vehicles.send(POST, "/api/v1/fleet/sweep", null);
    }
}
