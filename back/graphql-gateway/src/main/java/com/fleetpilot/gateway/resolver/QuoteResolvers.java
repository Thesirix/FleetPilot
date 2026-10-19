package com.fleetpilot.gateway.resolver;

import static com.fleetpilot.gateway.resolver.FleetQueries.orNull;
import static com.fleetpilot.gateway.resolver.FleetQueries.pageParams;
import static com.fleetpilot.gateway.resolver.FleetQueries.params;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.util.List;
import java.util.Map;

import com.fleetpilot.common.security.CurrentUser;
import com.fleetpilot.common.security.Roles;
import com.fleetpilot.gateway.client.ServiceClient;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

/**
 * The savings module through GraphQL: every field is one call to the quote service, which applies the rules (roles,
 * scope of the garage or the client company). The vehicle file gets its quotes from here: one screen, two services.
 */
@Controller
public class QuoteResolvers {

    private final ServiceClient quotes;

    public QuoteResolvers(ServiceClient quoteService) {
        this.quotes = quoteService;
    }

    // ---------------------------------------------------------------- queries

    @QueryMapping
    public Object quotes(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size,
            @Argument List<String> sort) {
        return quotes.get("/api/v1/quotes", pageParams(filter, page, size, sort));
    }

    @QueryMapping
    public Object quote(@Argument String id) {
        return orNull(() -> quotes.get("/api/v1/quotes/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object savings(@Argument String companyId) {
        return quotes.get("/api/v1/quotes/savings", params("companyId", companyId));
    }

    @QueryMapping
    public Object garages(@Argument Map<String, Object> filter, @Argument Integer page, @Argument Integer size,
            @Argument List<String> sort) {
        return quotes.get("/api/v1/garages", pageParams(filter, page, size, sort));
    }

    @QueryMapping
    public Object garage(@Argument String id) {
        return orNull(() -> quotes.get("/api/v1/garages/{id}", Map.of(), id));
    }

    @QueryMapping
    public Object myGarage() {
        return CurrentUser.hasAnyRole(Roles.GARAGE) ? orNull(() -> quotes.get("/api/v1/garages/me", Map.of())) : null;
    }

    @QueryMapping
    public Object tariff() {
        return quotes.get("/api/v1/tariff", Map.of());
    }

    @SchemaMapping(typeName = "Garage", field = "quotes")
    public Object garageQuotes(Map<String, Object> garage, @Argument Integer size) {
        return quotes.getMap("/api/v1/quotes", params("garageId", garage.get("id"), "size", size == null ? 10 : size))
                .get("content");
    }

    /** Quotes of a vehicle, for the vehicle file. Users without access to quotes (drivers) get an empty list. */
    @SchemaMapping(typeName = "Vehicle", field = "quotes")
    public Object vehicleQuotes(Map<String, Object> vehicle) {
        if (!CurrentUser.hasAnyRole(Roles.PLATFORM_ADMIN, Roles.FLEET_MANAGER, Roles.CLIENT_MANAGER)) {
            return List.of();
        }
        return quotes.getMap("/api/v1/quotes", params("vehicleId", vehicle.get("id"), "size", 50)).get("content");
    }

    @SchemaMapping(typeName = "Viewer", field = "garageId")
    public Object garageId(Object viewer) {
        return CurrentUser.garageId().orElse(null);
    }

    // ---------------------------------------------------------------- garage side

    @MutationMapping
    public Object submitQuote(@Argument Map<String, Object> input) {
        return quotes.send(POST, "/api/v1/quotes", input);
    }

    @MutationMapping
    public Object reviseQuote(@Argument String id, @Argument List<Map<String, Object>> lines, @Argument String message) {
        return quotes.send(POST, "/api/v1/quotes/{id}/revisions", params("lines", lines, "message", message), id);
    }

    @MutationMapping
    public Object answerCounterOffer(@Argument String id, @Argument Boolean accept, @Argument String message) {
        return quotes.send(POST, "/api/v1/quotes/{id}/counter-offer/answer",
                params("accept", accept, "message", message), id);
    }

    @MutationMapping
    public Object completeQuote(@Argument String id, @Argument String completedOn) {
        return quotes.send(POST, "/api/v1/quotes/{id}/completion", params("completedOn", completedOn), id);
    }

    @MutationMapping
    public Object invoiceQuote(@Argument String id, @Argument String number, @Argument Double amount,
            @Argument String invoicedOn) {
        return quotes.send(POST, "/api/v1/quotes/{id}/invoice",
                params("number", number, "amount", amount, "invoicedOn", invoicedOn), id);
    }

    // ---------------------------------------------------------------- manager side

    @MutationMapping
    public Object identifyQuoteVehicle(@Argument String id, @Argument String registration) {
        return quotes.send(POST, "/api/v1/quotes/{id}/vehicle", params("registration", registration), id);
    }

    @MutationMapping
    public Object counterOffer(@Argument String id, @Argument Double amount, @Argument String message) {
        return quotes.send(POST, "/api/v1/quotes/{id}/counter-offer", params("amount", amount, "message", message), id);
    }

    @MutationMapping
    public Object acceptQuote(@Argument String id, @Argument String comment) {
        return quotes.send(POST, "/api/v1/quotes/{id}/accept", params("comment", comment), id);
    }

    @MutationMapping
    public Object rejectQuote(@Argument String id, @Argument String reason) {
        return quotes.send(POST, "/api/v1/quotes/{id}/reject", params("reason", reason), id);
    }

    @MutationMapping
    public Object createGarage(@Argument Map<String, Object> input) {
        return quotes.send(POST, "/api/v1/garages", input);
    }

    @MutationMapping
    public Object updateGarage(@Argument String id, @Argument Map<String, Object> input) {
        return quotes.send(PUT, "/api/v1/garages/{id}", input, id);
    }

    @MutationMapping
    public Object approveGarage(@Argument String id, @Argument Boolean approved, @Argument Double rating) {
        return quotes.send(PUT, "/api/v1/garages/{id}/approval", params("approved", approved, "rating", rating), id);
    }

    @MutationMapping
    public Object updateLabourRate(@Argument String region, @Argument String category, @Argument Double hourlyRate,
            @Argument Double truckHourlyRate) {
        return quotes.send(PUT, "/api/v1/tariff/rates/{region}/{category}",
                params("hourlyRate", hourlyRate, "truckHourlyRate", truckHourlyRate), region, category);
    }
}
