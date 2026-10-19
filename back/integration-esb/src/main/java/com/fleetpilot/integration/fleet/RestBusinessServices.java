package com.fleetpilot.integration.fleet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.fleetpilot.integration.fleet.BusinessServices.CreatedQuote;
import com.fleetpilot.integration.fleet.BusinessServices.IncidentModule;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteModule;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteReference;
import com.fleetpilot.integration.fleet.BusinessServices.QuoteSubmission;
import com.fleetpilot.integration.fleet.BusinessServices.Refused;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The quote and fleet modules called over REST. A 4xx answer is a refusal of the content (turned into
 * {@link Refused}, with the error code of the service); a 5xx or a connection failure is left as it is, so that the
 * route retries it.
 */
public class RestBusinessServices implements QuoteModule, IncidentModule, QuoteReference {

    private final RestClient quotes;
    private final RestClient fleet;
    private final JsonMapper json;

    public RestBusinessServices(RestClient quotes, RestClient fleet, JsonMapper json) {
        this.quotes = quotes;
        this.fleet = fleet;
        this.json = json;
    }

    @Override
    public CreatedQuote submit(QuoteSubmission quote) {
        return refusals(() -> quotes.post().uri("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).body(quote)
                .retrieve().body(CreatedQuote.class));
    }

    record GarageRow(UUID id, String code) {
    }

    record GaragePage(List<GarageRow> content) {
    }

    @Override
    public Optional<Garage> garage(String code) {
        GaragePage page = quotes.get().uri(b -> b.path("/api/v1/garages").queryParam("q", code).queryParam("size", 20)
                .build()).retrieve().body(GaragePage.class);
        return page == null ? Optional.empty() : page.content().stream().filter(g -> code.equals(g.code())).findFirst()
                .map(g -> quotes.get().uri("/api/v1/garages/{id}", g.id()).retrieve().body(Garage.class));
    }

    @Override
    public Tariff tariff() {
        return quotes.get().uri("/api/v1/tariff").retrieve().body(Tariff.class);
    }

    record IncidentStatus(String status) {
    }

    @Override
    public String status(UUID incidentId) {
        return refusals(() -> fleet.get().uri("/api/v1/incidents/{id}", incidentId).retrieve()
                .body(IncidentStatus.class)).status();
    }

    @Override
    public void assess(UUID incidentId, String responsibility, BigDecimal estimatedCost) {
        refusals(() -> fleet.put().uri("/api/v1/incidents/{id}/assessment", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("responsibility", responsibility, "estimatedCost", estimatedCost))
                .retrieve().toBodilessEntity());
    }

    @Override
    public void moveTo(UUID incidentId, String status) {
        refusals(() -> fleet.patch().uri("/api/v1/incidents/{id}/status", incidentId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("status", status))
                .retrieve().toBodilessEntity());
    }

    private <T> T refusals(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            String code = "HTTP_" + e.getStatusCode().value();
            String message = e.getStatusText();
            try {
                JsonNode problem = json.readTree(e.getResponseBodyAsString());
                code = problem.path("code").asString(code);
                message = problem.path("detail").asString(message);
            } catch (RuntimeException unreadable) {
                // not a problem detail: keep the HTTP status
            }
            throw new Refused(code, message);
        }
    }
}
