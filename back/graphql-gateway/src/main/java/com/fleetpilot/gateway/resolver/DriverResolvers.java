package com.fleetpilot.gateway.resolver;

import static com.fleetpilot.gateway.resolver.FleetQueries.params;
import static org.springframework.http.HttpMethod.PUT;

import java.util.Map;

import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * The driver app: fill-ups, inspections and notifications in the vehicle service, garages near the driver in the quote
 * service. The writes use the idempotent PUT endpoints, keyed by the id generated on the phone.
 */
@Controller
public class DriverResolvers {

    private final ServiceClient vehicles;
    private final ServiceClient quotes;

    public DriverResolvers(ServiceClient vehicleService, ServiceClient quoteService) {
        this.vehicles = vehicleService;
        this.quotes = quoteService;
    }

    @QueryMapping
    public Object myFuelEntries() {
        return vehicles.getList("/api/v1/me/fuel-entries");
    }

    @QueryMapping
    public Object myInspections() {
        return vehicles.getList("/api/v1/me/inspections");
    }

    @QueryMapping
    public Object myNotifications() {
        return vehicles.getList("/api/v1/me/notifications");
    }

    @QueryMapping
    public Object nearbyGarages(@Argument Double latitude, @Argument Double longitude, @Argument Integer radiusKm,
            @Argument Integer limit) {
        return quotes.getListWithQuery("/api/v1/garages/nearby",
                params("latitude", latitude, "longitude", longitude, "radiusKm", radiusKm, "limit", limit));
    }

    @MutationMapping
    public Object recordMyFuel(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/me/fuel-entries/{id}", input, id);
    }

    @MutationMapping
    public Object recordMyInspection(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/me/inspections/{id}", input, id);
    }

    @MutationMapping
    public Object declareMyIncidentWithId(@Argument String id, @Argument Map<String, Object> input) {
        return vehicles.send(PUT, "/api/v1/me/incidents/{id}", input, id);
    }

    @SchemaMapping(typeName = "Vehicle", field = "fuelEntries")
    public Object fuelEntries(Map<String, Object> vehicle) {
        return vehicles.getList("/api/v1/vehicles/{id}/fuel-entries", vehicle.get("id"));
    }

    @SchemaMapping(typeName = "Vehicle", field = "inspections")
    public Object inspections(Map<String, Object> vehicle) {
        return vehicles.getList("/api/v1/vehicles/{id}/inspections", vehicle.get("id"));
    }

    @SchemaMapping(typeName = "Incident", field = "photos")
    public Object incidentPhotos(Map<String, Object> incident) {
        return vehicles.getList("/api/v1/incidents/{id}/photos", incident.get("id"));
    }
}
