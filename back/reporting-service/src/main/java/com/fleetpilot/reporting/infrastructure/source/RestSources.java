package com.fleetpilot.reporting.infrastructure.source;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.fleetpilot.reporting.application.Sources.Contract;
import com.fleetpilot.reporting.application.Sources.Deadline;
import com.fleetpilot.reporting.application.Sources.FleetSource;
import com.fleetpilot.reporting.application.Sources.Incident;
import com.fleetpilot.reporting.application.Sources.Quote;
import com.fleetpilot.reporting.application.Sources.QuoteSource;
import com.fleetpilot.reporting.application.Sources.Vehicle;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/** The fleet and quote services read page by page through their REST APIs, with the reporting service's identity. */
public final class RestSources {

    private static final int PAGE_SIZE = 100;

    private RestSources() {
    }

    /** The paginated shape shared by the FleetPilot APIs. */
    record Page<T>(List<T> content, int totalPages) {
    }

    record Company(UUID id, String name) {
    }

    /** Reads every page of a list. */
    static <T> List<T> all(RestClient rest, ParameterizedTypeReference<Page<T>> type,
            Function<Integer, Function<UriBuilder, URI>> uri) {
        List<T> all = new ArrayList<>();
        int page = 0;
        Page<T> current;
        do {
            current = rest.get().uri(uri.apply(page++)).retrieve().body(type);
            if (current != null) {
                all.addAll(current.content());
            }
        } while (current != null && page < current.totalPages());
        return all;
    }

    public static final class Fleet implements FleetSource {

        private final RestClient rest;

        public Fleet(RestClient rest) {
            this.rest = rest;
        }

        @Override
        public String companyName(UUID companyId) {
            Company company = rest.get().uri("/api/v1/companies/{id}", companyId).retrieve().body(Company.class);
            return company == null ? null : company.name();
        }

        @Override
        public List<Vehicle> vehicles(UUID companyId) {
            return all(rest, new ParameterizedTypeReference<>() { }, page -> builder -> builder.path("/api/v1/vehicles")
                    .queryParam("page", page).queryParam("size", PAGE_SIZE).queryParam("sort", "registration")
                    .queryParamIfPresent("companyId", Optional.ofNullable(companyId)).build());
        }

        @Override
        public List<Contract> activeContracts(UUID companyId) {
            return all(rest, new ParameterizedTypeReference<>() { }, page -> builder -> builder.path("/api/v1/contracts")
                    .queryParam("page", page).queryParam("size", PAGE_SIZE).queryParam("status", "ACTIVE")
                    .queryParamIfPresent("companyId", Optional.ofNullable(companyId)).build());
        }

        @Override
        public List<Incident> incidents(UUID companyId) {
            return all(rest, new ParameterizedTypeReference<>() { }, page -> builder -> builder.path("/api/v1/incidents")
                    .queryParam("page", page).queryParam("size", PAGE_SIZE).queryParam("sort", "occurredAt,desc")
                    .queryParamIfPresent("companyId", Optional.ofNullable(companyId)).build());
        }

        @Override
        public List<Deadline> urgentDeadlines(UUID companyId) {
            List<Deadline> deadlines = new ArrayList<>();
            for (String urgency : List.of("OVERDUE", "DUE_SOON")) {
                deadlines.addAll(all(rest, new ParameterizedTypeReference<Page<Deadline>>() { },
                        page -> builder -> builder.path("/api/v1/deadlines")
                                .queryParam("page", page).queryParam("size", PAGE_SIZE).queryParam("urgency", urgency)
                                .queryParam("sort", "dueDate,asc")
                                .queryParamIfPresent("companyId", Optional.ofNullable(companyId)).build()));
            }
            return deadlines;
        }
    }

    public static final class Quotes implements QuoteSource {

        private final RestClient rest;

        public Quotes(RestClient rest) {
            this.rest = rest;
        }

        @Override
        public List<Quote> quotes(UUID companyId, Instant from, Instant to) {
            return all(rest, new ParameterizedTypeReference<>() { }, page -> builder -> builder.path("/api/v1/quotes")
                    .queryParam("page", page).queryParam("size", PAGE_SIZE).queryParam("sort", "submittedAt,asc")
                    .queryParamIfPresent("companyId", Optional.ofNullable(companyId))
                    .queryParamIfPresent("from", Optional.ofNullable(from))
                    .queryParamIfPresent("to", Optional.ofNullable(to)).build());
        }

        @Override
        public Map<String, Object> savings(UUID companyId) {
            return rest.get().uri(builder -> builder.path("/api/v1/quotes/savings")
                            .queryParamIfPresent("companyId", Optional.ofNullable(companyId)).build())
                    .retrieve().body(new ParameterizedTypeReference<Map<String, Object>>() { });
        }
    }
}
